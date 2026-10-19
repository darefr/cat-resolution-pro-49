package dev.cat.resolution;

final class ShizukuConnectionState {
    private long bindingGeneration;
    private boolean binding;
    private boolean requestingPermission;
    private boolean permissionDenied;
    private boolean automaticReconnectUsed;

    synchronized void resetReconnectAttempts() { automaticReconnectUsed = false; }

    synchronized boolean takeAutomaticReconnect() {
        if (automaticReconnectUsed) return false;
        automaticReconnectUsed = true;
        return true;
    }

    synchronized long beginBinding() {
        binding = true;
        return ++bindingGeneration;
    }

    synchronized boolean finishBinding(long generation) {
        if (!binding || generation != bindingGeneration) return false;
        binding = false;
        return true;
    }

    synchronized boolean isCurrentBinding(long generation) {
        return generation == bindingGeneration;
    }

    synchronized boolean isBinding() { return binding; }
    synchronized boolean isRequestingPermission() { return requestingPermission; }

    synchronized void invalidateBinding() {
        ++bindingGeneration;
        binding = false;
    }

    synchronized void permissionRequested() {
        requestingPermission = true;
        permissionDenied = false;
    }

    synchronized void permissionResult(boolean granted) {
        requestingPermission = false;
        permissionDenied = !granted;
    }

    synchronized void permissionRequestTimedOut() {
        requestingPermission = false;
    }

    synchronized void serverStopped() {
        invalidateBinding();
        resetReconnectAttempts();
        requestingPermission = false;
        permissionDenied = false;
    }

    synchronized String status(boolean serverRunning, boolean authorized, boolean serviceConnected) {
        if (!serverRunning) return "stopped";
        if (authorized) {
            if (serviceConnected) return "ready";
            return binding ? "connecting" : "error";
        }
        if (requestingPermission) return "requesting";
        return permissionDenied ? "denied" : "permission";
    }
}
