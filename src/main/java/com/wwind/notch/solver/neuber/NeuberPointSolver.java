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
 *  - every non-zero load solves the coupled equation. Ramberg-Osgood has no
 *    truly elastic range — the plastic term (sigma/K)^(1/n) is non-zero at any
 *    stress — so the elastic extrapolation sigma = Kt*sigma_n is never returned
 *    as the "true" answer, and the response is continuous and strictly
 *    monotone in the nominal stress;
 *  - the ELASTIC/PLASTIC label follows {@link RambergOsgood#plasticStrainNegligible}:
 *    ELASTIC only when the plastic strain is negligible next to the elastic
 *    strain. An ELASTIC label still carries the honest coupled root and its
 *    (tiny) plastic strain; the label never hides plasticity;
 *  - Kt == 1 (no notch): degenerates to the plain uniaxial Ramberg-Osgood answer,
 *    i.e. sigma = sigma_n and epsilon = RO(sigma_n), plastic part included;
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

        double energy = magnitude * magnitude / law.elasticModulus();
        double rootMagnitude = solvePlasticRoot(law, magnitude, energy);
        double trueStress = Math.copySign(rootMagnitude, elasticStress);

        double elasticPart = law.elasticStrain(trueStress);
        double plasticPart = law.plasticStrain(trueStress);
        double totalStrain = elasticPart + plasticPart;
        verifyNeuberResidual(trueStress, totalStrain, energy);

        Regime regime = law.plasticStrainNegligible(trueStress) ? Regime.ELASTIC : Regime.PLASTIC;
        return new NeuberSolution(trueStress, totalStrain, elasticPart, plasticPart, regime);
    }

    private NeuberSolution uniaxial(RambergOsgood law, double nominalStress) {
        double elasticPart = law.elasticStrain(nominalStress);
        double plasticPart = law.plasticStrain(nominalStress);
        Regime regime = law.plasticStrainNegligible(nominalStress) ? Regime.ELASTIC : Regime.PLASTIC;
        return new NeuberSolution(nominalStress, elasticPart + plasticPart,
                elasticPart, plasticPart, regime);
    }

    private double solvePlasticRoot(RambergOsgood law, double elasticStressMagnitude, double energy) {
        // At sigma=0 the residual is -energy < 0; at the elastic extrapolation
        // sigma = Kt*sigma_n it equals sigma * plasticStrain(sigma) > 0, so the
        // coupled root is bracketed inside [0, Kt*sigma_n]. Expand defensively.
        double lower = 0.0;
        double upper = elasticStressMagnitude;
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
