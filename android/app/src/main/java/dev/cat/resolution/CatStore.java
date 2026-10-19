package dev.cat.resolution;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;

public final class CatStore extends SQLiteOpenHelper {
    public CatStore(Context context) { super(context, "cat-resolution.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE presets (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, multiplier REAL NOT NULL CHECK(multiplier BETWEEN 1.01 AND 1.30))");
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT NOT NULL, multiplier REAL NOT NULL, detail TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("A migration is required before changing the database version.");
    }

    public synchronized JSONArray presets() throws Exception {
        JSONArray results = new JSONArray();
        try (Cursor cursor = getReadableDatabase().query("presets", new String[]{"id", "name", "multiplier"}, null, null, null, null, "id ASC")) {
            while (cursor.moveToNext()) results.put(new JSONObject().put("id", cursor.getLong(0)).put("name", cursor.getString(1)).put("multiplier", cursor.getDouble(2)));
        }
        return results;
    }

    public synchronized void savePreset(String name, double multiplier) {
        DisplaySpec.validateMultiplier(multiplier);
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 32 || clean.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Use a preset name between 1 and 32 characters.");
        SQLiteDatabase db = getWritableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM presets", null)) {
            cursor.moveToFirst();
            if (cursor.getInt(0) >= 16) throw new IllegalArgumentException("You can save up to 16 custom presets. Delete one first.");
        }
        ContentValues values = new ContentValues();
        values.put("name", clean);
        values.put("multiplier", multiplier);
        db.insertOrThrow("presets", null, values);
    }

    public synchronized void deletePreset(long id) {
        if (id <= 0) throw new IllegalArgumentException("Built-in presets cannot be deleted.");
        if (getWritableDatabase().delete("presets", "id = ?", new String[]{Long.toString(id)}) != 1) throw new IllegalArgumentException("This preset no longer exists.");
    }

    public synchronized String setting(String key, String fallback) {
        try (Cursor cursor = getReadableDatabase().query("settings", new String[]{"value"}, "key = ?", new String[]{key}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : fallback;
        }
    }

    private void putSetting(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        getWritableDatabase().insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized JSONObject preferences() throws Exception {
        return new JSONObject().put("overlayOpacity", Integer.parseInt(setting("overlayOpacity", "90")))
            .put("overlaySize", Integer.parseInt(setting("overlaySize", "56")))
            .put("restoreOnLock", Boolean.parseBoolean(setting("restoreOnLock", "true")))
            .put("safetyTimeout", Integer.parseInt(setting("safetyTimeout", "15")))
            .put("language", setting("language", "en"));
    }

    public synchronized void savePreferences(JSONObject input) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Iterator<String> keys = input.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value;
                switch (key) {
                    case "overlayOpacity":
                        int opacity = input.getInt(key);
                        if (opacity < 50 || opacity > 100) throw new IllegalArgumentException("Opacity must be between 50 and 100.");
                        value = Integer.toString(opacity);
                        break;
                    case "overlaySize":
                        int size = input.getInt(key);
                        if (size < 40 || size > 80) throw new IllegalArgumentException("Overlay size must be between 40 and 80.");
                        value = Integer.toString(size);
                        break;
                    case "restoreOnLock": value = Boolean.toString(input.getBoolean(key)); break;
                    case "safetyTimeout": value = Integer.toString(DisplaySpec.validateTimeout(input.getInt(key))); break;
                    case "language":
                        value = input.getString(key);
                        if (!"en".equals(value) && !"pt".equals(value)) throw new IllegalArgumentException("Unsupported language.");
                        break;
                    default: throw new IllegalArgumentException("Unknown setting.");
                }
                putSetting(key, value);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void recordDisplay(JSONObject display) throws Exception {
        long revision = display.optLong("revision", 0);
        if (revision == 0 || Long.toString(revision).equals(setting("lastRevision", "0"))) return;
        String action = display.optString("lastOutcome", "");
        if (action.isEmpty()) return;
        int width = display.getInt("currentWidth");
        int height = display.getInt("currentHeight");
        double multiplier = (double) Math.max(width, height) / Math.max(display.getInt("nativeWidth"), display.getInt("nativeHeight"));
        ContentValues values = new ContentValues();
        values.put("action", action);
        values.put("multiplier", multiplier);
        values.put("detail", width + " × " + height + " · " + display.optInt("density") + " dpi");
        values.put("created_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.insertOrThrow("history", null, values);
            putSetting("lastRevision", Long.toString(revision));
            db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 100)");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized JSONArray history() throws Exception {
        JSONArray rows = new JSONArray();
        try (Cursor cursor = getReadableDatabase().query("history", new String[]{"id", "action", "multiplier", "detail", "created_at"}, null, null, null, null, "id DESC", "100")) {
            while (cursor.moveToNext()) rows.put(new JSONObject().put("id", cursor.getLong(0)).put("action", cursor.getString(1)).put("multiplier", cursor.getDouble(2)).put("detail", cursor.getString(3)).put("createdAt", cursor.getLong(4)));
        }
        return rows;
    }

    public synchronized void clearHistory() { getWritableDatabase().delete("history", null, null); }
}
