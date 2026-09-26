package com.wwind.notch.model;

/**
 * Response regime at the notch root.
 * ELASTIC: local response stays on the Hooke line (true stress == Kt * nominal stress).
 * PLASTIC: Neuber / Ramberg-Osgood coupled root, plastic strain participates.
 */
public enum Regime {
    ELASTIC,
    PLASTIC
}
