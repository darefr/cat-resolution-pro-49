package dev.cat.resolution;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShizukuConnectionStateTest {
    @Test public void pairingDoesNotMeanServerIsRunning() {
        assertEquals("stopped", new ShizukuConnectionState().status(false, false, false));
    }

    @Test public void runningServerStillRequiresAppPermission() {
        assertEquals("permission", new ShizukuConnectionState().status(true, false, false));
    }

    @Test public void permissionPromptHasItsOwnState() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        state.permissionRequested();
        assertEquals("requesting", state.status(true, false, false));
    }

    @Test public void denialIsNotReportedAsStillWaiting() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        state.permissionRequested();
        state.permissionResult(false);
        assertEquals("denied", state.status(true, false, false));
        assertFalse(state.isRequestingPermission());
    }

    @Test public void authorizationInManagerOverridesPreviousDenial() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        state.permissionResult(false);
        state.beginBinding();
        assertEquals("connecting", state.status(true, true, false));
    }

    @Test public void authorizedDisconnectedServiceNeverAsksForPermissionAgain() {
        assertEquals("error", new ShizukuConnectionState().status(true, true, false));
    }

    @Test public void authorizationAloneDoesNotMeanDisplayControlIsReady() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        state.permissionResult(true);
        state.beginBinding();
        assertEquals("connecting", state.status(true, true, false));
    }

    @Test public void connectedServiceNeedsLiveServerAndPermission() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        assertEquals("stopped", state.status(false, true, true));
        assertEquals("permission", state.status(true, false, true));
        assertEquals("ready", state.status(true, true, true));
    }

    @Test public void expiredOldAttemptCannotCancelNewConnection() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        long first = state.beginBinding();
        state.invalidateBinding();
        long second = state.beginBinding();
        assertFalse(state.finishBinding(first));
        assertTrue(state.isBinding());
        assertTrue(state.finishBinding(second));
    }

    @Test public void completedAttemptCannotBeTimedOutAgain() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        long attempt = state.beginBinding();
        assertTrue(state.finishBinding(attempt));
        assertFalse(state.finishBinding(attempt));
        assertEquals("ready", state.status(true, true, true));
    }

    @Test public void serverRestartInvalidatesOldServiceCallbacks() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        long oldAttempt = state.beginBinding();
        state.permissionRequested();
        state.serverStopped();
        assertFalse(state.isCurrentBinding(oldAttempt));
        assertFalse(state.isBinding());
        assertFalse(state.isRequestingPermission());
        assertEquals("stopped", state.status(false, false, false));
    }

    @Test public void missingPermissionDialogCanBeRetried() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        state.permissionRequested();
        state.permissionRequestTimedOut();
        assertEquals("permission", state.status(true, false, false));
        state.permissionRequested();
        assertEquals("requesting", state.status(true, false, false));
    }

    @Test public void crashingServiceDoesNotCreateAnEndlessReconnectLoop() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        assertTrue(state.takeAutomaticReconnect());
        assertFalse(state.takeAutomaticReconnect());
        state.resetReconnectAttempts();
        assertTrue(state.takeAutomaticReconnect());
    }

    @Test public void serverRestartAllowsOneFreshAutomaticReconnect() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        assertTrue(state.takeAutomaticReconnect());
        state.serverStopped();
        assertTrue(state.takeAutomaticReconnect());
        assertFalse(state.takeAutomaticReconnect());
    }

    @Test public void failedBindIsReconnectableWithoutReauthorizing() {
        ShizukuConnectionState state = new ShizukuConnectionState();
        long attempt = state.beginBinding();
        assertTrue(state.finishBinding(attempt));
        assertEquals("error", state.status(true, true, false));
        state.beginBinding();
        assertEquals("connecting", state.status(true, true, false));
    }
}
