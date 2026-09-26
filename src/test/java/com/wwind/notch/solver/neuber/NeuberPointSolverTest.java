package com.wwind.notch.solver.neuber;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.Regime;
import com.wwind.notch.solver.BisectionRootFinder;
import com.wwind.notch.solver.ConvergenceException;
import com.wwind.notch.solver.RootFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeuberPointSolverTest {

    private static final double E = 200_000;
    private static final double K = 1_200;
    private static final double N = 0.2;

    private final RambergOsgood law = new RambergOsgood(E, K, N);
    private final NeuberPointSolver solver = new NeuberPointSolver(new BisectionRootFinder());

    @Test
    void coupledRootIsSolvedAtEveryLoadNotJustAboveTheProportionalLimit() {
        // Kt*100 = 300 MPa is below the 0.2% proportional limit (~346 MPa), yet
        // the R-O plastic strain is already ~35% of the elastic strain there.
        // There is no elastic shortcut: the coupled root is 257.75 MPa and the
        // plastic component is reported honestly.
        NeuberSolution s = solver.solve(law, 3.0, 100.0);

        assertEquals(257.7475, s.trueStress(), 1e-3);
        assertEquals(0.0017458949, s.trueTotalStrain(), 1e-9);
        assertTrue(s.truePlasticStrain() > 0.0, "塑性分量必须按 R-O 真实上报，不能抹成 0");
        assertEquals(Regime.PLASTIC, s.regime());
    }

    @Test
    void tinyLoadStaysElasticWithNegligiblePlasticStrain() {
        // Kt*10 = 30 MPa: plastic strain ~0.01% of the elastic strain, tag ELASTIC,
        // but the numbers remain the coupled root (30 MPa to within 0.01).
        NeuberSolution s = solver.solve(law, 3.0, 10.0);

        assertEquals(30.0, s.trueStress(), 0.01);
        assertEquals(1.5e-4, s.trueTotalStrain(), 1e-7);
        assertEquals(Regime.ELASTIC, s.regime());
        assertTrue(s.truePlasticStrain() > 0.0 && s.truePlasticStrain() < 1e-6);
    }

    @Test
    void zeroNominalStressGivesZeroResponse() {
        NeuberSolution s = solver.solve(law, 3.0, 0.0);
        assertEquals(0.0, s.trueStress(), 0.0);
        assertEquals(0.0, s.trueTotalStrain(), 0.0);
        assertEquals(Regime.ELASTIC, s.regime());
    }

    @Test
    void plasticTrueStressRelaxesBelowElasticExtrapolationAndStrainGrowsFaster() {
        double nominal = 400.0;
        double kt = 3.0;
        NeuberSolution s = solver.solve(law, kt, nominal);

        double elasticStress = kt * nominal;
        assertEquals(Regime.PLASTIC, s.regime());
        assertTrue(s.trueStress() < elasticStress,
                "塑性后真实应力必须低于弹性外推 Kt*sigma_n");
        assertTrue(s.truePlasticStrain() > 0.0);
        assertTrue(s.trueTotalStrain() > elasticStress / E,
                "塑性后真实应变必须比弹性外推增长更快");

        // Neuber equality sigma*epsilon = (Kt*sigma_n)^2 / E.
        double lhs = s.trueStress() * s.trueTotalStrain();
        double rhs = elasticStress * elasticStress / E;
        assertEquals(rhs, lhs, rhs * 1e-10);

        // Constitutive split epsilon = sigma/E + (sigma/K)^(1/n).
        double expectedStrain = s.trueStress() / E
                + Math.pow(s.trueStress() / K, 1.0 / N);
        assertEquals(expectedStrain, s.trueTotalStrain(), expectedStrain * 1e-10);
    }

    @Test
    void ktOneDegeneratesToUniaxialRambergOsgoodInPlasticRegime() {
        double nominal = 800.0;
        NeuberSolution s = solver.solve(law, 1.0, nominal);

        assertEquals(nominal, s.trueStress(), 0.0);
        assertEquals(law.totalStrain(nominal), s.trueTotalStrain(), 1e-14);
        assertEquals(law.plasticStrain(nominal), s.truePlasticStrain(), 1e-14);
        assertEquals(Regime.PLASTIC, s.regime());
    }

    @Test
    void ktOneUniaxialWithNegligiblePlasticShareIsTaggedElastic() {
        NeuberSolution s = solver.solve(law, 1.0, 100.0);
        assertEquals(100.0, s.trueStress(), 0.0);
        assertEquals(law.totalStrain(100.0), s.trueTotalStrain(), 1e-15);
        assertEquals(Regime.ELASTIC, s.regime());
    }

    @Test
    void ktOneUniaxialAlwaysReportsFullRambergOsgoodStrain() {
        // sigma_n = 300: plastic strain is already 65% of the elastic strain.
        // The old hard switch reported eps = sigma/E = 0.0015, plastic 0;
        // the true uniaxial R-O answer is 0.0024765625.
        NeuberSolution s = solver.solve(law, 1.0, 300.0);
        assertEquals(300.0, s.trueStress(), 0.0);
        assertEquals(0.0024765625, s.trueTotalStrain(), 1e-12);
        assertEquals(0.0009765625, s.truePlasticStrain(), 1e-12);
        assertEquals(Regime.PLASTIC, s.regime());
    }

    @Test
    void largerKtIsMoreSevereAtSameNominalStress() {
        double nominal = 300.0;
        NeuberSolution mild = solver.solve(law, 2.0, nominal);
        NeuberSolution sharp = solver.solve(law, 3.0, nominal);

        assertTrue(sharp.trueTotalStrain() > mild.trueTotalStrain(),
                "Kt 越大，同一名义应力下缺口根部应变越大");
        assertTrue(sharp.trueStress() > mild.trueStress());
    }

    @Test
    void stressGrowthSlowsWhileStrainGrowthAcceleratesIntoPlasticity() {
        NeuberSolution a = solver.solve(law, 3.0, 200.0); // elastic boundary-ish
        NeuberSolution b = solver.solve(law, 3.0, 300.0);
        NeuberSolution c = solver.solve(law, 3.0, 400.0);

        double stressRate1 = (b.trueStress() - a.trueStress()) / 100.0;
        double stressRate2 = (c.trueStress() - b.trueStress()) / 100.0;
        double strainRate1 = (b.trueTotalStrain() - a.trueTotalStrain()) / 100.0;
        double strainRate2 = (c.trueTotalStrain() - b.trueTotalStrain()) / 100.0;

        assertTrue(stressRate2 < stressRate1, "进入塑性后应力增长应放缓");
        assertTrue(strainRate2 > strainRate1, "进入塑性后应变增长应加快");
    }

    @Test
    void compressionIsOddSymmetricToTension() {
        NeuberSolution t = solver.solve(law, 3.0, 400.0);
        NeuberSolution c = solver.solve(law, 3.0, -400.0);
        assertEquals(t.trueStress(), -c.trueStress(), 1e-9);
        assertEquals(t.trueTotalStrain(), -c.trueTotalStrain(), 1e-12);
    }

    @Test
    void nonConvergingRootIsReportedAsErrorNotANumber() {
        // A finder that always refuses to bracket a root must surface failure.
        RootFinder broken = (function, lower, upper) -> {
            throw new ConvergenceException("stub finder: no bracket");
        };
        NeuberPointSolver failing = new NeuberPointSolver(broken);

        ConvergenceException ex = assertThrows(ConvergenceException.class,
                () -> failing.solve(law, 3.0, 400.0));
        assertTrue(ex.getMessage().contains("stub finder"));
    }
}
