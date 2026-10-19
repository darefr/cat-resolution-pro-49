package dev.cat.resolution;

import android.app.Application;

public final class CatApplication extends Application {
    private AppController controller;
    @Override public void onCreate() {
        super.onCreate();
        controller = new AppController(this);
    }
    public AppController controller() { return controller; }
}
