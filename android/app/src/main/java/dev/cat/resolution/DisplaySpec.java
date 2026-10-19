package dev.cat.resolution;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DisplaySpec {
    private static final Pattern PHYSICAL = Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)");
    private static final Pattern OVERRIDE = Pattern.compile("Override size:\\s*(\\d+)x(\\d+)");
    private static final Pattern SIZE = Pattern.compile("(\\d{2,5})x(\\d{2,5})");
    public final int nativeWidth;
    public final int nativeHeight;
    public final int width;
    public final int height;
    public final String overrideSize;

    private DisplaySpec(int nativeWidth, int nativeHeight, int width, int height, String overrideSize) {
        validateDimensions(nativeWidth, nativeHeight);
        validateDimensions(width, height);
        this.nativeWidth = nativeWidth;
        this.nativeHeight = nativeHeight;
        this.width = width;
        this.height = height;
        this.overrideSize = overrideSize;
    }

    public static DisplaySpec parse(String output) {
        Matcher physical = PHYSICAL.matcher(output);
        if (!physical.find()) throw new IllegalArgumentException("Android did not report a physical display size.");
        int nativeWidth = Integer.parseInt(physical.group(1));
        int nativeHeight = Integer.parseInt(physical.group(2));
        Matcher override = OVERRIDE.matcher(output);
        if (override.find()) {
            int width = Integer.parseInt(override.group(1));
            int height = Integer.parseInt(override.group(2));
            return new DisplaySpec(nativeWidth, nativeHeight, width, height, width + "x" + height);
        }
        return new DisplaySpec(nativeWidth, nativeHeight, nativeWidth, nativeHeight, "reset");
    }

    public String projectedSize(double multiplier) {
        validateMultiplier(multiplier);
        int targetWidth = nativeWidth >= nativeHeight ? (int) Math.round(nativeWidth * multiplier) : nativeWidth;
        int targetHeight = nativeHeight > nativeWidth ? (int) Math.round(nativeHeight * multiplier) : nativeHeight;
        validateDimensions(targetWidth, targetHeight);
        return targetWidth + "x" + targetHeight;
    }

    public static void validateMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 1.01 || multiplier > 1.30) {
            throw new IllegalArgumentException("The multiplier must be between 1.01 and 1.30.");
        }
    }

    public static String validateOverride(String value) {
        if ("reset".equals(value)) return value;
        Matcher size = SIZE.matcher(value == null ? "" : value);
        if (!size.matches()) throw new IllegalArgumentException("Invalid display dimensions.");
        validateDimensions(Integer.parseInt(size.group(1)), Integer.parseInt(size.group(2)));
        return value;
    }

    public static int validateTimeout(int timeout) {
        if (timeout != 15 && timeout != 20 && timeout != 30) {
            throw new IllegalArgumentException("Confirmation must be 15, 20, or 30 seconds.");
        }
        return timeout;
    }

    private static void validateDimensions(int width, int height) {
        if (width < 200 || height < 200 || width > 10000 || height > 10000) {
            throw new IllegalArgumentException("Display dimensions are outside the supported range.");
        }
    }
}
