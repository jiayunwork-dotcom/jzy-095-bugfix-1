package com.wwind.notch.acceptance;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.LoadingSequenceResult;
import com.wwind.notch.model.NotchPointResult;
import com.wwind.notch.model.Regime;
import com.wwind.notch.model.SequencePointResult;
import com.wwind.notch.service.NotchAssessmentService;
import com.wwind.notch.solver.BisectionRootFinder;
import com.wwind.notch.solver.neuber.NeuberPointSolver;
import com.wwind.notch.solver.sequence.LoadingSequenceSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收测试（内置算例材料 E=200000, K=1200, n=0.2）。
 *
 * 核心一条 {@link #increasingSweepsAreMonotoneContinuousAndConstitutivelyConsistent}
 * 沿递增序列逐点核：单调不减、相邻点不许跳（增量带解析上界，0.5 MPa 细密网格）、
 * Neuber 等式与 R-O 本构分解逐点成立（相对误差 1e-8 量级）。
 *
 * 其余测试钉住手算锚点（Kt=3/100、Kt=3/150、Kt=1/300）、旧硬切换断层位置
 * （115→116、Kt=2 的 172→174、Kt=4 的 86→87、Kt=1 的 346→347）、
 * 负载镜像、标签与塑性应变占比的一致、单点/批量完全一致。
 */
class NotchAcceptanceCriteriaTest {

    private static final double E = 200_000.0;
    private static final double K = 1_200.0;
    private static final double N = 0.2;
    private static final double NEUBER_TOL = 1.0e-8;

    private final RambergOsgood law = new RambergOsgood(E, K, N);
    private final NotchAssessmentService service =
            new NotchAssessmentService(new NeuberPointSolver(new BisectionRootFinder()));
    private final LoadingSequenceSolver sequenceSolver = new LoadingSequenceSolver(service);

    private NotchPointResult assess(double kt, double nominal) {
        return service.assessPoint(law, kt, nominal);
    }

    private static double roStrain(double stress) {
        return stress / E + Math.pow(Math.abs(stress) / K, 1.0 / N);
    }

    @Test
    void increasingSweepsAreMonotoneContinuousAndConstitutivelyConsistent() {
        for (double kt : new double[]{1.0, 2.0, 3.0, 4.0}) {
            double step = 0.5;
            // 增量解析上界用最深一点的斜率定（g' 随 sigma 单调增大）：
            //   d(sigma)/d(sn) <= kt, d(eps)/d(sn) <= kt * g'(sigma_max).
            double deepestStress = Math.abs(assess(kt, 400.0).trueStress());
            double maxStrainRate = kt * (1.0 / E
                    + (1.0 / N) * Math.pow(deepestStress / K, 1.0 / N - 1.0) / K);

            NotchPointResult prev = null;
            for (int i = 0; i <= 800; i++) {
                double nominal = i * step;
                NotchPointResult p = assess(kt, nominal);

                // 本构分解: eps = sigma/E + (|sigma|/K)^(1/n)
                assertEquals(roStrain(p.trueStress()), p.trueTotalStrain(),
                        Math.max(1.0e-12, p.trueTotalStrain() * 1.0e-10),
                        () -> "Kt=" + kt + " sn=" + nominal + " 不满足 R-O 本构");
                // 弹/塑分量之和等于总应变。
                assertEquals(p.trueElasticStrain() + p.truePlasticStrain(),
                        p.trueTotalStrain(), 1.0e-15,
                        () -> "Kt=" + kt + " sn=" + nominal + " 弹塑分量之和不等于总应变");
                if (kt > 1.0 && nominal > 0.0) {
                    // Neuber 等式: sigma*eps = (Kt*sn)^2/E，相对误差 1e-8 量级。
                    double rhs = (kt * nominal) * (kt * nominal) / E;
                    assertEquals(rhs, p.trueStress() * p.trueTotalStrain(),
                            rhs * NEUBER_TOL,
                            () -> "Kt=" + kt + " sn=" + nominal + " 不满足 Neuber 等式");
                } else if (kt == 1.0) {
                    assertEquals(nominal, p.trueStress(), 0.0,
                            "Kt=1 时真实应力必须等于名义应力");
                }

                if (prev != null) {
                    final NotchPointResult from = prev;
                    double dNominal = nominal - (i - 1) * step;
                    assertTrue(p.trueStress() >= from.trueStress() - 1.0e-9,
                            () -> "Kt=" + kt + " sn=" + nominal + " 真实应力回落: "
                                    + from.trueStress() + " -> " + p.trueStress());
                    assertTrue(p.trueTotalStrain() >= from.trueTotalStrain() - 1.0e-12,
                            () -> "Kt=" + kt + " sn=" + nominal + " 真实应变回落");
                    // 不许跳：相邻增量不得超过单调耦合解的解析上界。
                    assertTrue(p.trueStress() - from.trueStress() <= kt * dNominal + 1.0e-9,
                            () -> "Kt=" + kt + " sn=" + nominal + " 应力增量超界（疑似跳变）");
                    assertTrue(p.trueTotalStrain() - from.trueTotalStrain()
                                    <= maxStrainRate * dNominal * 1.05 + 1.0e-12,
                            () -> "Kt=" + kt + " sn=" + nominal + " 应变增量超界（疑似跳变）");
                }
                prev = p;
            }
        }
    }

    @Test
    void userReproSequence110To120IsSmoothAndBatchMatchesPoints() {
        List<Double> nominals = List.of(110.0, 112.0, 114.0, 115.0, 116.0, 118.0, 120.0);
        LoadingSequenceResult seq = sequenceSolver.solve(law, 3.0, nominals);

        NotchPointResult prev = null;
        for (int i = 0; i < nominals.size(); i++) {
            SequencePointResult sp = seq.points().get(i);
            NotchPointResult p = sp.point();

            double rhs = (3.0 * p.nominalStress()) * (3.0 * p.nominalStress()) / E;
            assertEquals(rhs, p.trueStress() * p.trueTotalStrain(), rhs * NEUBER_TOL);
            assertEquals(roStrain(p.trueStress()), p.trueTotalStrain(),
                    p.trueTotalStrain() * 1.0e-10);

            // 批量接口与单点接口对同一输入给同一组数（record 全等比较）。
            assertEquals(assess(3.0, nominals.get(i)), p);

            if (prev != null) {
                double gap = p.nominalStress() - prev.nominalStress();
                assertTrue(p.trueStress() > prev.trueStress(), "115→116 处真实应力不得回落");
                assertTrue(p.trueTotalStrain() > prev.trueTotalStrain());
                assertTrue(p.trueStress() - prev.trueStress() <= 3.0 * gap + 1.0e-9,
                        "相邻点应力增量超界");
            }
            prev = p;
        }

        // 旧断层位置：旧实现 115 给 345（弹性）、116 掉到 282.8。
        assertEquals(281.3507, seq.points().get(3).point().trueStress(), 1.0e-3);
        assertEquals(282.8092, seq.points().get(4).point().trueStress(), 1.0e-3);
        assertEquals(0.0021152429, seq.points().get(3).point().trueTotalStrain(), 1.0e-9);
        assertEquals(0.0021410905, seq.points().get(4).point().trueTotalStrain(), 1.0e-9);
    }

    @Test
    void kt3Nominal100MatchesHandSolvedCoupledRoot() {
        NotchPointResult p = assess(3.0, 100.0);
        assertEquals(257.7475, p.trueStress(), 1.0e-3);
        assertEquals(0.0017458949, p.trueTotalStrain(), 1.0e-9);
        assertTrue(p.truePlasticStrain() > 0.0);
        assertEquals(300.0, p.elasticStress(), 0.0);
        assertEquals(Regime.PLASTIC, p.regime());
    }

    @Test
    void kt3Nominal150MatchesCrossCheckedDeepPlasticValue() {
        NotchPointResult p = assess(3.0, 150.0);
        assertEquals(325.8952, p.trueStress(), 1.0e-3);
        assertEquals(0.0031068269, p.trueTotalStrain(), 1.0e-9);
    }

    @Test
    void negligiblePlasticLoadStaysElasticWithHonestTinyPlasticComponent() {
        NotchPointResult p = assess(3.0, 10.0);
        assertEquals(30.0, p.trueStress(), 0.01);
        assertEquals(1.5e-4, p.trueTotalStrain(), 1.0e-7);
        assertEquals(Regime.ELASTIC, p.regime());
        // 标签为弹性，塑性分量仍是真实的极小值而不是被抹成 0。
        assertTrue(p.truePlasticStrain() > 0.0 && p.truePlasticStrain() < 1.0e-6);
    }

    @Test
    void kt1Nominal300IsExactUniaxialRambergOsgood() {
        NotchPointResult p = assess(1.0, 300.0);
        assertEquals(300.0, p.trueStress(), 0.0);
        assertEquals(0.0024765625, p.trueTotalStrain(), 1.0e-12);
        assertEquals(0.0009765625, p.truePlasticStrain(), 1.0e-12);
        assertEquals(Regime.PLASTIC, p.regime());
    }

    @Test
    void kt1StrainIsContinuousAcrossTheProportionalLimit() {
        // 旧硬切换在 346→347 让应变从 0.00173 一步蹦到 0.00376。
        NotchPointResult a = assess(1.0, 346.0);
        NotchPointResult b = assess(1.0, 347.0);
        assertEquals(0.0037228484, a.trueTotalStrain(), 1.0e-9);
        assertEquals(0.0037568137, b.trueTotalStrain(), 1.0e-9);
        assertTrue(b.trueTotalStrain() - a.trueTotalStrain() < 1.0e-4,
                "跨过比例极限时应变不应发生 0.002 量级的跳变");
    }

    @Test
    void noStressDropAtFormerSwitchPointsForKt2AndKt4() {
        // 旧实现的硬切换位置：Kt=2 在 172~174 之间、Kt=4 在 86~87 之间。
        assertEquals(280.8616, assess(2.0, 172.0).trueStress(), 1.0e-3);
        assertEquals(282.8092, assess(2.0, 174.0).trueStress(), 1.0e-3);
        assertTrue(assess(2.0, 174.0).trueStress() > assess(2.0, 172.0).trueStress());

        assertEquals(280.8616, assess(4.0, 86.0).trueStress(), 1.0e-3);
        assertEquals(282.8092, assess(4.0, 87.0).trueStress(), 1.0e-3);
        assertTrue(assess(4.0, 87.0).trueStress() > assess(4.0, 86.0).trueStress());

        // 同一 |Kt*sn|（344、348 MPa）在不同 Kt 下必须给出同一个根。
        assertEquals(assess(2.0, 172.0).trueStress(), assess(4.0, 86.0).trueStress(), 1.0e-9);
        assertEquals(assess(2.0, 174.0).trueStress(), assess(4.0, 87.0).trueStress(), 1.0e-9);
    }

    @Test
    void compressionIsMirrorImageOfTensionIncludingTag() {
        for (double kt : new double[]{1.0, 2.0, 3.0, 4.0}) {
            for (double nominal : new double[]{10.0, 50.0, 100.0, 115.0, 116.0, 300.0, 400.0}) {
                NotchPointResult t = assess(kt, nominal);
                NotchPointResult c = assess(kt, -nominal);
                assertEquals(t.trueStress(), -c.trueStress(), 1.0e-9,
                        () -> "Kt=" + kt + " sn=" + nominal + " 应力不镜像");
                assertEquals(t.trueTotalStrain(), -c.trueTotalStrain(), 1.0e-12,
                        () -> "Kt=" + kt + " sn=" + nominal + " 应变不镜像");
                assertEquals(t.truePlasticStrain(), -c.truePlasticStrain(), 1.0e-15);
                assertEquals(t.regime(), c.regime(), "镜像点标签应一致");
            }
        }
    }

    @Test
    void regimeTagAlwaysFollowsPlasticStrainShareAndNeverHidesPlasticStrain() {
        for (double kt : new double[]{1.0, 2.0, 3.0, 4.0}) {
            for (int i = 0; i <= 100; i++) {
                double nominal = i * 4.0;
                NotchPointResult p = assess(kt, nominal);

                // 塑性分量始终按 R-O 真实上报，与标签无关。
                assertEquals(law.plasticStrain(p.trueStress()), p.truePlasticStrain(), 0.0);

                boolean negligible = Math.abs(p.truePlasticStrain())
                        <= 0.05 * Math.abs(p.trueElasticStrain());
                assertEquals(negligible ? Regime.ELASTIC : Regime.PLASTIC, p.regime(),
                        () -> "Kt=" + kt + " sn=" + nominal + " 标签与塑性应变占比不一致");
            }
        }
    }

    @Test
    void batchSequenceEqualsSinglePointAssessmentsForAllRows() {
        List<Double> nominals = List.of(0.0, -300.0, -100.0, 10.0, 50.0, 100.0,
                115.0, 116.0, 150.0, 300.0, 400.0);
        LoadingSequenceResult seq = sequenceSolver.solve(law, 3.0, nominals);
        for (int i = 0; i < nominals.size(); i++) {
            assertEquals(assess(3.0, nominals.get(i)), seq.points().get(i).point());
        }
    }
}
