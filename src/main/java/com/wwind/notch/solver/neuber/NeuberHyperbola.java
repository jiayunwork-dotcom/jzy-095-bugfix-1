package com.wwind.notch.solver.neuber;

import com.wwind.notch.constitutive.RambergOsgood;

/**
 * The Neuber hyperbola for one loading point:
 * <pre>
 *     sigma * epsilon = (Kt * sigma_n)^2 / E
 * </pre>
 * It relates the pseudo-elastic energy density (Kt*sigma_n extrapolated) to the
 * true elastic-plastic energy density at the notch root. Pure algebra, stateless;
 * root bracketing and iteration live in {@link NeuberPointSolver}.
 */
public final class NeuberHyperbola {

    private NeuberHyperbola() {
    }

    /** Pseudo-elastic notch stress: Kt * sigma_n. */
    public static double elasticNotchStress(double kt, double nominalStress) {
        return kt * nominalStress;
    }

    /** Right-hand side energy density of the Neuber rule (signed). */
    public static double neuberEnergyDensity(RambergOsgood law, double kt, double nominalStress) {
        double s = elasticNotchStress(kt, nominalStress);
        return s * s / law.elasticModulus();
    }

    /**
     * Coupling residual for a candidate true stress:
     * sigma * epsilon_R-O(sigma) - (Kt*sigma_n)^2 / E.
     * Its zero is the Neuber/Ramberg-Osgood coupled solution.
     */
    public static double residual(RambergOsgood law, double neuberEnergyDensity, double trueStress) {
        return trueStress * law.totalStrain(trueStress) - neuberEnergyDensity;
    }
}
