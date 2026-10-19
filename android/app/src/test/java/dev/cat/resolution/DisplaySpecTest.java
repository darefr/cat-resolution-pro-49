package dev.cat.resolution;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplaySpecTest {
    @Test public void parsesPhysicalPortraitDisplay() {
        DisplaySpec display = DisplaySpec.parse("Physical size: 1080x2400\n");
        assertEquals(1080, display.nativeWidth);
        assertEquals(2400, display.nativeHeight);
        assertEquals("reset", display.overrideSize);
    }

    @Test public void preservesExistingOverrideForRecovery() {
        DisplaySpec display = DisplaySpec.parse("Physical size: 1080x2400\nOverride size: 900x2000\n");
        assertEquals("900x2000", display.overrideSize);
        assertEquals(900, display.width);
        assertEquals(2000, display.height);
    }

    @Test public void scalesLongEdgeInPortrait() {
        assertEquals("1080x2784", DisplaySpec.parse("Physical size: 1080x2400").projectedSize(1.16));
    }

    @Test public void scalesLongEdgeInLandscape() {
        assertEquals("2784x1080", DisplaySpec.parse("Physical size: 2400x1080").projectedSize(1.16));
    }

    @Test public void doesNotCompoundPreviousScaling() {
        assertEquals("1080x2640", DisplaySpec.parse("Physical size: 1080x2400\nOverride size: 1080x2784").projectedSize(1.10));
    }

    @Test public void supportsBoundaryMultipliers() {
        assertEquals("1080x2424", DisplaySpec.parse("Physical size: 1080x2400").projectedSize(1.01));
        assertEquals("1080x3120", DisplaySpec.parse("Physical size: 1080x2400").projectedSize(1.30));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsNaN() { DisplaySpec.validateMultiplier(Double.NaN); }
    @Test(expected = IllegalArgumentException.class) public void rejectsInfinity() { DisplaySpec.validateMultiplier(Double.POSITIVE_INFINITY); }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnsafeScaling() { DisplaySpec.validateMultiplier(8); }
    @Test(expected = IllegalArgumentException.class) public void rejectsBelowMinimum() { DisplaySpec.validateMultiplier(1); }
    @Test(expected = IllegalArgumentException.class) public void rejectsShellInjection() { DisplaySpec.validateOverride("1080x2400; reboot"); }
    @Test(expected = IllegalArgumentException.class) public void rejectsCommandSubstitution() { DisplaySpec.validateOverride("$(id)"); }
    @Test(expected = IllegalArgumentException.class) public void rejectsSmallDimensions() { DisplaySpec.validateOverride("10x10"); }
    @Test(expected = IllegalArgumentException.class) public void rejectsMissingPhysicalSize() { DisplaySpec.parse("SecurityException: permission denied"); }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnknownTimeout() { DisplaySpec.validateTimeout(0); }
    @Test public void acceptsOnlySupportedTimeouts() { for (int seconds : new int[]{15,20,30}) assertEquals(seconds, DisplaySpec.validateTimeout(seconds)); }
    @Test public void validatesReset() { assertEquals("reset", DisplaySpec.validateOverride("reset")); }
}
