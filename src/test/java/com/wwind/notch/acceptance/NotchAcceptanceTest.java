package com.wwind.notch.acceptance;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.example.BuiltInExample;
import com.wwind.notch.model.LoadingSequenceResult;
import com.wwind.notch.model.MaterialParameters;
import com.wwind.notch.model.NotchPointResult;
import com.wwind.notch.model.Regime;
import com.wwind.notch.service.NotchAssessmentService;
import com.wwind.notch.solver.BisectionRootFinder;
import com.wwind.notch.solver.neuber.NeuberPointSolver;
import com.wwind.notch.solver.sequence.LoadingSequenceSolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance checks for the notch-root assessment kernel, mirroring the review
 * criteria for the elastic-plastic transition fix:
 *
 *  - along any increasing nominal sequence the true stress/strain are monotone
 *    non-decreasing with no jumps between adjacent points;
 *  - every point satisfies sigma*epsilon = (Kt*sigma_n)^2/E (Kt &gt; 1) and
 *    epsilon = sigma/E + (sigma/K)^(1/n) to ~1e-8 relative error;
 *  - Kt = 1 degenerates to plain uniaxial Ramberg-Osgood (sigma = sigma_n);
 *  - hand-calculation anchors: Kt=3/nominal 100 -&gt; ~257.7 MPa / ~0.001746,
 *    Kt=1/nominal 300 -&gt; 0.0024766 / plastic 0.0009766,
 *    deep-plastic Kt=3/nominal 150 -&gt; ~325.9 MPa;
 *  - the ELASTIC label means negligible plastic strain, never "plasticity
 *    silently dropped"; genuinely elastic points (Kt=3/nominal 10) keep it;
 *  - tension/compression mirror symmetry and single-point vs batch agreement.
 */
class NotchAcceptanceTest {

    private static final double E = 200_000;
    private static final double K = 1_200;
    private static final double N = 0.2;
    private static final double REL_TOL = 1e-8;

    private final MaterialParameters material = new MaterialParameters(E, K, N);
    private final RambergOsgood law = new RambergOsgood(material);
    private final NotchAssessmentService service =
            new NotchAssessmentService(new NeuberPointSolver(new BisectionRootFinder()));
    private final LoadingSequenceSolver sequenceSolver = new LoadingSequenceSolver(service);

    @Test
    void increasingSweepIsMonotoneSmoothAndConstitutivelyConsistent() {
        for (double kt : new double[]{1.0, 1.5, 2.0, 2.5, 3.0, 4.0}) {
            // 1 MPa steps straight across every former elastic/plastic cut-over.
            List<Double> sweep = new ArrayList<>();
            for (int sn = 1; sn <= 500; sn++) {
                sweep.add((double) sn);
            }
            LoadingSequenceResult result = sequenceSolver.solve(law, kt, sweep);

            NotchPointResult prev = null;
            for (var sequencePoint : result.points()) {
                NotchPointResult cur = sequencePoint.point();
                double sn = cur.nominalStress();
                String where = "Kt=" + kt + ", sigma_n=" + sn;

                // Reported decomposition must recompose the total strain.
                assertEquals(cur.trueTotalStrain(),
                        cur.trueElasticStrain() + cur.truePlasticStrain(),
                        Math.abs(cur.trueTotalStrain()) * 1e-12, where);

                // Constitutive law: |epsilon| = |sigma|/E + (|sigma|/K)^(1/n).
                double roStrain = Math.abs(cur.trueStress()) / E
                        + Math.pow(Math.abs(cur.trueStress()) / K, 1.0 / N);
                assertEquals(roStrain, Math.abs(cur.trueTotalStrain()), roStrain * REL_TOL, where);

                if (kt > 1.0) {
                    // Neuber equality sigma*epsilon = (Kt*sigma_n)^2/E.
                    double rhs = kt * sn * kt * sn / E;
                    assertEquals(rhs, cur.trueStress() * cur.trueTotalStrain(),
                            rhs * REL_TOL, where);
                    // True stress never exceeds the elastic extrapolation.
                    assertTrue(cur.trueStress() <= kt * sn * (1.0 + 1e-12), where);
                } else {
                    // Kt = 1 degenerates to plain uniaxial Ramberg-Osgood.
                    assertEquals(sn, cur.trueStress(), 0.0, where);
                }

                if (prev != null) {
                    double dSn = sn - prev.nominalStress();
                    // Monotone non-decreasing under increasing load.
                    assertTrue(cur.trueStress() >= prev.trueStress(),
                            () -> "true stress drops at " + where);
                    assertTrue(cur.trueTotalStrain() >= prev.trueTotalStrain(),
                            () -> "true strain drops at " + where);
                    // No jumps: the coupled stress grows slower than the elastic
                    // extrapolation slope, and the strain increment stays within a
                    // generous physical bound (actual slopes here are <= ~1.5e-4).
                    double dSigma = cur.trueStress() - prev.trueStress();
                    double dEpsilon = cur.trueTotalStrain() - prev.trueTotalStrain();
                    assertTrue(dSigma <= kt * dSn * (1.0 + 1e-9),
                            () -> "stress jump of " + dSigma + " MPa at " + where);
                    assertTrue(dEpsilon <= 50.0 * kt * dSn / E,
                            () -> "strain jump of " + dEpsilon + " at " + where);
                }
                prev = cur;
            }
        }
    }

    @Test
    void kt3Nominal100MatchesHandCalculation() {
        NotchPointResult r = service.assess(material, 3.0, 100.0);
        assertEquals(257.7475, r.trueStress(), 1e-3);
        assertEquals(0.0017459, r.trueTotalStrain(), 1e-7);
        assertEquals(Regime.PLASTIC, r.regime());
        assertTrue(r.truePlasticStrain() > 1.0e-4,
                "塑性应变已达弹性应变的约 35%，绝不可报 0 或挂弹性标签");
    }

    @Test
    void kt1Nominal300IsPlainUniaxialRambergOsgood() {
        NotchPointResult r = service.assess(material, 1.0, 300.0);
        assertEquals(300.0, r.trueStress(), 0.0);
        assertEquals(0.0024765625, r.trueTotalStrain(), 1e-10);
        assertEquals(0.0009765625, r.truePlasticStrain(), 1e-10);
        assertEquals(Regime.PLASTIC, r.regime());
    }

    @Test
    void deepPlasticPointKeepsKnownGoodValue() {
        // Regression: the deep-plastic branch was already correct (verified externally).
        NotchPointResult r = service.assess(material, 3.0, 150.0);
        assertEquals(325.8952, r.trueStress(), 1e-3);
        assertEquals(Regime.PLASTIC, r.regime());
    }

    @Test
    void negligiblePlasticityKeepsElasticLabelAndHonestNumbers() {
        NotchPointResult r = service.assess(material, 3.0, 10.0);
        assertEquals(Regime.ELASTIC, r.regime());
        assertEquals(30.0, r.trueStress(), 1e-2);
        assertEquals(1.5e-4, r.trueTotalStrain(), 1e-7);
        assertTrue(Math.abs(r.truePlasticStrain())
                <= RambergOsgood.NEGLIGIBLE_PLASTIC_FRACTION * Math.abs(r.trueElasticStrain()));
    }

    @Test
    void regimeLabelFlipsWherePlasticStrainStartsToMatter() {
        // The label boundary sits where eps_p reaches 0.1% of eps_e
        // (Kt*sigma_n ~ 59.4 MPa), not at the 0.2% offset stress ~346 MPa.
        assertEquals(Regime.ELASTIC, service.assess(material, 3.0, 19.0).regime());
        assertEquals(Regime.PLASTIC, service.assess(material, 3.0, 20.0).regime());
    }

    @Test
    void formerJumpLocationsAreNowSmooth() {
        // Old cut-over faults: Kt=3 at 115/116, Kt=2 at 173/174,
        // Kt=4 at 86/87, Kt=1 at 346/347.
        assertSmoothAcross(3.0, 115.0, 116.0);
        assertSmoothAcross(2.0, 173.0, 174.0);
        assertSmoothAcross(4.0, 86.0, 87.0);
        assertSmoothAcross(1.0, 346.0, 347.0);
    }

    private void assertSmoothAcross(double kt, double sn1, double sn2) {
        NotchPointResult a = service.assess(material, kt, sn1);
        NotchPointResult b = service.assess(material, kt, sn2);
        String where = "Kt=" + kt + " between sigma_n=" + sn1 + " and " + sn2;
        assertTrue(b.trueStress() > a.trueStress(), () -> "true stress not increasing: " + where);
        assertTrue(b.trueTotalStrain() > a.trueTotalStrain(),
                () -> "true strain not increasing: " + where);
        assertTrue(b.trueStress() - a.trueStress() <= kt * (sn2 - sn1) * (1.0 + 1e-9),
                () -> "stress jump at " + where);
        assertTrue(b.trueTotalStrain() - a.trueTotalStrain() <= 50.0 * kt * (sn2 - sn1) / E,
                () -> "strain jump at " + where);
    }

    @Test
    void reportedSequence110To120IsContinuous() {
        // The exact nominal-stress sweep from the defect report.
        LoadingSequenceResult result = sequenceSolver.solve(law, 3.0,
                List.of(110.0, 112.0, 114.0, 115.0, 116.0, 118.0, 120.0));

        NotchPointResult prev = null;
        for (var p : result.points()) {
            NotchPointResult cur = p.point();
            double rhs = 3.0 * cur.nominalStress() * 3.0 * cur.nominalStress() / E;
            assertEquals(rhs, cur.trueStress() * cur.trueTotalStrain(), rhs * REL_TOL);
            if (prev != null) {
                assertTrue(cur.trueStress() > prev.trueStress(),
                        "115 -> 116 一带不允许再出现应力回落");
                assertTrue(cur.trueTotalStrain() > prev.trueTotalStrain());
            }
            prev = cur;
        }
    }

    @Test
    void negativeLoadsMirrorPositiveLoads() {
        for (double kt : new double[]{1.0, 2.0, 3.0, 4.0}) {
            for (double sn : new double[]{10.0, 50.0, 100.0, 115.0, 116.0, 200.0, 346.0, 347.0, 500.0}) {
                NotchPointResult tension = service.assess(material, kt, sn);
                NotchPointResult compression = service.assess(material, kt, -sn);
                assertEquals(tension.trueStress(), -compression.trueStress(), 1e-9);
                assertEquals(tension.trueTotalStrain(), -compression.trueTotalStrain(), 1e-12);
                assertEquals(tension.trueElasticStrain(), -compression.trueElasticStrain(), 1e-15);
                assertEquals(tension.truePlasticStrain(), -compression.truePlasticStrain(), 1e-15);
                assertEquals(tension.regime(), compression.regime());
            }
        }
    }

    @Test
    void singlePointAndSequenceSolveGiveIdenticalNumbers() {
        for (double kt : new double[]{1.0, 2.0, 3.0, 4.0}) {
            for (double sn : new double[]{10.0, 100.0, 115.0, 116.0, 300.0, 400.0}) {
                NotchPointResult single = service.assess(material, kt, sn);
                NotchPointResult fromBatch = sequenceSolver.solve(law, kt, List.of(sn))
                        .points().get(0).point();
                assertEquals(single, fromBatch, "Kt=" + kt + ", sigma_n=" + sn);
            }
        }
    }

    @Test
    void builtInExampleHeaderStatesActualOffsetReferenceStress() {
        LoadingSequenceResult result = sequenceSolver.solve(law, BuiltInExample.KT,
                BuiltInExample.NOMINAL_STRESSES);
        String table = BuiltInExample.renderTable(result, law);
        assertTrue(table.contains("346.2"),
                "表头比例极限必须打印实际值 K*0.002^n = 346.2 MPa");
        assertFalse(table.contains("359"), "表头不得再打印错误的 359 MPa");
    }
}
