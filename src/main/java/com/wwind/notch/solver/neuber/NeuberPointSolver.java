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
 *  - the coupled root is solved for EVERY non-zero load when Kt &gt; 1. There is
 *    deliberately no "elastic shortcut" branch: a hard switch at the
 *    proportional limit was doubly wrong. Below the switch it hid real plastic
 *    strain (reported sigma = Kt*sigma_n, plastic strain 0 although R-O already
 *    carries an appreciable plastic part), and at the switch itself it produced
 *    a jump discontinuity — the nominal stress rising by 1 MPa while the notch
 *    root stress dropped by tens of MPa under monotonic loading. Solving the
 *    coupled root always keeps the response continuous and monotone in
 *    |Kt*sigma_n|: sigma stays slightly below Kt*sigma_n and strain slightly
 *    above sigma/E from the smallest load, the offsets growing smoothly;
 *  - Kt == 1 (no notch): degenerates to the plain uniaxial Ramberg-Osgood
 *    answer, i.e. sigma = sigma_n and epsilon = RO(sigma_n);
 *  - the ELASTIC/PLASTIC tag is descriptive only — it follows the plastic
 *    strain share ({@link #NEGLIGIBLE_PLASTIC_STRAIN_RATIO}) and never feeds
 *    back into the reported stress, strain or plastic components;
 *  - a root that does not converge or fails its residual check raises
 *    {@link ConvergenceException}; a suspicious number is never returned.
 */
@Component
public class NeuberPointSolver {

    /** Accepted relative error of the converged root against the Neuber equality. */
    private static final double RESIDUAL_REL_TOLERANCE = 1.0e-8;
    /** How far the search bracket may grow while chasing a sign change. */
    private static final int BRACKET_EXPANSIONS = 256;
    /**
     * Plastic strain at or below this fraction of the elastic strain is treated
     * as negligible, and the point is tagged ELASTIC. Labelling convention
     * only: it changes no reported number — the plastic component is always the
     * true R-O value, even while the tag reads ELASTIC.
     */
    static final double NEGLIGIBLE_PLASTIC_STRAIN_RATIO = 0.05;

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
        if (energy == 0.0) {
            // |Kt*sigma_n| so small that sigma^2/E underflowed to zero: the
            // coupled root equals the elastic extrapolation to machine
            // precision and the plastic part is genuinely zero as a double.
            double strain = elasticStress / law.elasticModulus();
            return new NeuberSolution(elasticStress, strain, strain, 0.0, Regime.ELASTIC);
        }

        double rootMagnitude = solveCoupledRoot(law, magnitude, energy);
        double trueStress = Math.copySign(rootMagnitude, elasticStress);

        double elasticPart = law.elasticStrain(trueStress);
        double plasticPart = law.plasticStrain(trueStress);
        double totalStrain = elasticPart + plasticPart;
        verifyNeuberResidual(trueStress, totalStrain, energy);

        return new NeuberSolution(trueStress, totalStrain, elasticPart, plasticPart,
                regimeOf(elasticPart, plasticPart));
    }

    private NeuberSolution uniaxial(RambergOsgood law, double nominalStress) {
        // Always the full R-O strain: sigma/E + (sigma/K)^(1/n), never a
        // hard-switched pure-Hooke value, so the uniaxial curve is smooth.
        double elasticPart = law.elasticStrain(nominalStress);
        double plasticPart = law.plasticStrain(nominalStress);
        return new NeuberSolution(nominalStress, elasticPart + plasticPart,
                elasticPart, plasticPart, regimeOf(elasticPart, plasticPart));
    }

    /**
     * ELASTIC when the plastic strain component is negligible next to the
     * elastic component, PLASTIC otherwise. Sign-folded; at zero stress the
     * comparison 0 &le; 0 yields ELASTIC. The tag never alters any number.
     */
    private static Regime regimeOf(double elasticStrain, double plasticStrain) {
        return Math.abs(plasticStrain) <= NEGLIGIBLE_PLASTIC_STRAIN_RATIO * Math.abs(elasticStrain)
                ? Regime.ELASTIC : Regime.PLASTIC;
    }

    private double solveCoupledRoot(RambergOsgood law, double elasticStressMagnitude, double energy) {
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
