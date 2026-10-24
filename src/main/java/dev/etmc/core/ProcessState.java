package dev.etmc.core;

/** Lifecycle states of the managed EasyTier Core child process (plan §7.2). */
public enum ProcessState {
    STOPPED,
    STARTING,
    CONNECTING,
    CONNECTED,
    READY,
    RECONNECTING,
    STOPPING,
    ERROR;

    /** True once the child process has been observed alive past the grace window. */
    public boolean isAliveish() {
        return this == CONNECTING || this == CONNECTED || this == READY || this == RECONNECTING;
    }
}
