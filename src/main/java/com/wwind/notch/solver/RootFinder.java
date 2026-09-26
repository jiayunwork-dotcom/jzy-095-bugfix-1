package com.wwind.notch.solver;

import java.util.function.DoubleUnaryOperator;

/**
 * Scalar root finder contract. Hand-written only: no external math libraries.
 */
public interface RootFinder {

    /**
     * Finds x in [lower, upper] with f(x) == 0.
     *
     * @throws ConvergenceException when no sign change brackets a root or the
     *                              iteration budget is exhausted before tolerance
     */
    double findRoot(DoubleUnaryOperator function, double lower, double upper);
}
