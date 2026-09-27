package com.wwind.notch.model;

/**
 * Response regime at the notch root, labelled by whether plastic strain matters.
 * ELASTIC: plastic strain is negligible (within 0.1% of the elastic strain) —
 *          the reported numbers are still the coupled Neuber/Ramberg-Osgood root.
 * PLASTIC: plastic strain is non-negligible and participates in the response.
 */
public enum Regime {
    ELASTIC,
    PLASTIC
}
