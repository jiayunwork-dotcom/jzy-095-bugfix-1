package com.wwind.notch.service;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.MaterialParameters;
import com.wwind.notch.model.NotchPointResult;
import com.wwind.notch.solver.neuber.NeuberPointSolver;
import com.wwind.notch.solver.neuber.NeuberSolution;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one notch-root assessment: builds the constitutive law from request
 * parameters, invokes the Neuber coupled solver, and attaches the pure-elastic
 * extrapolation (Kt*sigma_n and Kt*sigma_n / E) for side-by-side comparison.
 */
@Service
public class NotchAssessmentService {

    private final NeuberPointSolver pointSolver;

    public NotchAssessmentService(NeuberPointSolver pointSolver) {
        this.pointSolver = pointSolver;
    }

    public NotchPointResult assess(MaterialParameters material, double kt, double nominalStress) {
        RambergOsgood law = new RambergOsgood(material);
        return assessPoint(law, kt, nominalStress);
    }

    public NotchPointResult assessPoint(RambergOsgood law, double kt, double nominalStress) {
        NeuberSolution solution = pointSolver.solve(law, kt, nominalStress);
        double elasticStress = kt * nominalStress;
        double elasticStrain = elasticStress / law.elasticModulus();
        return new NotchPointResult(nominalStress, kt, elasticStress, elasticStrain,
                solution.trueStress(), solution.trueTotalStrain(),
                solution.trueElasticStrain(), solution.truePlasticStrain(),
                solution.regime());
    }
}
