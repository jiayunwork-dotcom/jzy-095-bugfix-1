package com.wwind.notch.model;

/**
 * Descriptive response tag at the notch root.
 * ELASTIC: the plastic strain component is negligible next to the elastic
 * component (share at or below {@code NEGLIGIBLE_PLASTIC_STRAIN_RATIO} in
 * {@code NeuberPointSolver}).
 * PLASTIC: plastic strain participates appreciably.
 *
 * The tag is labelling only: true stress/strain are always the coupled
 * Neuber/Ramberg-Osgood values and the plastic component is always reported,
 * so an ELASTIC point may carry a tiny non-zero plastic strain.
 */
public enum Regime {
    ELASTIC,
    PLASTIC
}
