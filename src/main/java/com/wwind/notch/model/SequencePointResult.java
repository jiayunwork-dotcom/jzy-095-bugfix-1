package com.wwind.notch.model;

/**
 * One point of a loading sequence: the independent point assessment plus a thin
 * unloading-layer marker and an elastically-unloaded local stress/strain hint.
 *
 * When segment == LOADING the hint fields equal the coupled solution; on UNLOADING
 * they are computed by elastic rebound from the running maximum (no Neuber coupling,
 * no reverse plasticity loop is tracked).
 */
public record SequencePointResult(NotchPointResult point,
                                  Segment segment,
                                  double runningMaxNominalStress,
                                  double localStressHint,
                                  double localStrainHint) {
}
