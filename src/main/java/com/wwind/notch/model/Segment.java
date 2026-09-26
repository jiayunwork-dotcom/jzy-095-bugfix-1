package com.wwind.notch.model;

/**
 * Segment marker used by the thin unloading support in a loading sequence.
 * Only the monotonic tension envelope is coupled with Neuber; UNLOADING points
 * are unloaded elastically from the running maximum (thin-layer rule only).
 */
public enum Segment {
    LOADING,
    UNLOADING,
    NEUTRAL
}
