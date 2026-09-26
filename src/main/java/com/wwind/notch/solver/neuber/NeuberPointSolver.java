package com.wwind.notch.solver.neuber;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.Regime;
import com.wwind.notch.solver.ConvergenceException;
import com.wwind.notch.solver.RootFinder;
import org.springframework.stereotype.Component;

/**
 * Couples the Neuber hyperbola with the monotonic Ramberg-Osgood law for one
 * nominal stress and returns the notch-root true stress and strain.
 *
 * Solved equation (folded onto the tension envelope by sign):
 * <pre>
 *     sigma * (sigma/E + (sigma/K)^(1/n)) = (Kt*sigma_n)^2 / E
 * </pre>
 *
 * Behavioural guarantees:
 *  - elastic regime (|Kt*sigma_n| within the 0.2% proportional limit):
 *    true stress = Kt*sigma_n, true strain = sigma/E exactly;
 *  - plastic regime: the coupled root is strictly below the elastic extrapolation
 *    while strain keeps growing faster than the Hooke line;
 *  - Kt == 1 (no notch): degenerates to the plain uniaxial Ramberg-Osgood answer,
 *    i.e. sigma = sigma_n and epsilon = RO(sigma_n);
 *  - a root that does not converge or fails its residual check raises
 *    {@link ConvergenceException}; a suspicious number is never returned.
 */
@Component
public class NeuberPointSolver {

    /** Accepted relative error of the converged root against the Neuber equality. */
    private static final double RESIDUAL_REL_TOLERANCE = 1.0e-8;
    /** How far the search bracket may grow while chasing a sign change. */
    private static final int BRACKET_EXPANSIONS = 256;

    private final RootFinder rootFinder;

    public NeuberPointSolver(RootFinder rootFinder) {
        this.rootFinder = rootFinder;
    }

    public NeuberSolution solve(RambergOsgood law, double kt, double nominalStress) {
        double elasticStress = NeuberHyperbola.elasticNotchStress(kt, nominalStress);
        double magnitude = Math.abs(elasticStress);

        // Unnotched body: Neuber degenerates to plain uniaxial Ramberg-Osgood.
        if (kt == 1.0) {
            return uniaxial(law, nominalStress);
        }
        if (magnitude == 0.0) {
            return new NeuberSolution(0.0, 0.0, 0.0, 0.0, Regime.ELASTIC);
        }

        double proportionalLimit = law.proportionalLimitStress();
        if (magnitude <= proportionalLimit) {
            // Hooke regime: exact elastic relationships, no plastic correction.
            double strain = elasticStress / law.elasticModulus();
            return new NeuberSolution(elasticStress, strain, strain, 0.0, Regime.ELASTIC);
        }

        double energy = magnitude * magnitude / law.elasticModulus();
        double rootMagnitude = solvePlasticRoot(law, magnitude, proportionalLimit, energy);
        double trueStress = Math.copySign(rootMagnitude, elasticStress);

        double elasticPart = law.elasticStrain(trueStress);
        double plasticPart = law.plasticStrain(trueStress);
        double totalStrain = elasticPart + plasticPart;
        verifyNeuberResidual(trueStress, totalStrain, energy);

        return new NeuberSolution(trueStress, totalStrain, elasticPart, plasticPart, Regime.PLASTIC);
    }

    private NeuberSolution uniaxial(RambergOsgood law, double nominalStress) {
        double magnitude = Math.abs(nominalStress);
        Regime regime = magnitude <= law.proportionalLimitStress() ? Regime.ELASTIC : Regime.PLASTIC;
        double elasticPart = law.elasticStrain(nominalStress);
        double plasticPart = regime == Regime.PLASTIC ? law.plasticStrain(nominalStress) : 0.0;
        return new NeuberSolution(nominalStress, elasticPart + plasticPart,
                elasticPart, plasticPart, regime);
    }

    private double solvePlasticRoot(RambergOsgood law, double elasticStressMagnitude,
                                    double proportionalLimit, double energy) {
        // At sigma=0 the residual is -energy < 0; at the elastic extrapolation
        // sigma = Kt*sigma_n it equals sigma * plasticStrain(sigma) > 0, so the
        // coupled root is bracketed inside [0, Kt*sigma_n]. Expand defensively.
        double lower = 0.0;
        double upper = Math.max(elasticStressMagnitude, proportionalLimit);
        double fLower = NeuberHyperbola.residual(law, energy, lower);
        double fUpper = NeuberHyperbola.residual(law, energy, upper);

        for (int i = 0; i < BRACKET_EXPANSIONS && (!Double.isFinite(fUpper) || fUpper <= 0.0); i++) {
            upper *= 2.0;
            fUpper = NeuberHyperbola.residual(law, energy, upper);
        }
        if (!Double.isFinite(fLower) || fLower >= 0.0 || !Double.isFinite(fUpper) || fUpper <= 0.0) {
            throw new ConvergenceException(String.format(
                    "Neuber 联立方程无法夹住根: 弹性外推应力=%.6g, 端残差 f(0)=%.6g, f(%.6g)=%.6g",
                    elasticStressMagnitude, fLower, upper, fUpper));
        }
        return rootFinder.findRoot(
                sigma -> NeuberHyperbola.residual(law, energy, sigma), lower, upper);
    }

    private void verifyNeuberResidual(double trueStress, double totalStrain, double energy) {
        double actualEnergy = trueStress * totalStrain;
        double scale = Math.max(Math.abs(energy), 1.0);
        double relativeError = Math.abs(actualEnergy - energy) / scale;
        if (Double.isNaN(relativeError) || relativeError > RESIDUAL_REL_TOLERANCE) {
            throw new ConvergenceException(String.format(
                    "Neuber 联立根未通过残差校核: |sigma*eps - RHS|/scale = %.3g 超过容差 %.0e",
                    relativeError, RESIDUAL_REL_TOLERANCE));
        }
    }
}
