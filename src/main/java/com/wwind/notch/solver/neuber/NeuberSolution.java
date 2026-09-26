package com.wwind.notch.solver.neuber;

import com.wwind.notch.model.Regime;

/**
 * Numerical outcome of one coupled notch-root solve.
 */
public record NeuberSolution(double trueStress,
                             double trueTotalStrain,
                             double trueElasticStrain,
                             double truePlasticStrain,
                             Regime regime) {
}
