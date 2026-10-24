package dev.etmc.network;

/**
 * Network-level phase, independent from the child process state.
 * Process alive != network connected != host reachable.
 */
public enum NetworkPhase {
    /** Not querying (process not running or manager stopped). */
    IDLE,
    /** RPC portal not answering yet (core still booting). */
    RPC_WAIT,
    /** RPC answers but no remote peer yet. */
    NO_PEERS,
    /** At least one remote peer visible. */
    PEERS_SEEN,
    /** Configured host virtual IP present in route table. */
    HOST_ONLINE,
    /** Host was online before but disappeared; still trying. */
    HOST_LOST,
    /** RPC died while process still alive (rare; treat as reconnecting). */
    RPC_LOST
}
