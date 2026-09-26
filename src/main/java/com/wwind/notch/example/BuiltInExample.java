package com.wwind.notch.example;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.LoadingSequenceRequest;
import com.wwind.notch.model.LoadingSequenceResult;
import com.wwind.notch.model.MaterialParameters;

import java.util.List;
import java.util.Locale;

/**
 * Built-in rounded-notch example: Kt = 3.
 * Steel-like monotonic Ramberg-Osgood constants (stresses in MPa):
 *   E = 200000 MPa, K = 1200 MPa, n = 0.20.
 * 0.2% proportional limit: K * 0.002^n ~= 346 MPa (printed from the law itself).
 *
 * Every row is the coupled Neuber/Ramberg-Osgood root: from the smallest load
 * the true stress sits just below Kt*sigma_n while strain runs just ahead of
 * the Hooke line, and the offsets grow smoothly with loading. The ELASTIC /
 * PLASTIC tag only reflects the plastic strain share (negligible up to ~54 MPa
 * nominal here), so the tag flips well below the proportional limit while the
 * numbers stay continuous across it.
 */
public final class BuiltInExample {

    public static final double E = 200_000.0;
    public static final double K = 1_200.0;
    public static final double N = 0.20;
    public static final double KT = 3.0;

    public static final List<Double> NOMINAL_STRESSES =
            List.of(0.0, 50.0, 100.0, 120.0, 150.0, 200.0, 250.0, 300.0, 350.0, 400.0);

    private BuiltInExample() {
    }

    public static MaterialParameters material() {
        return new MaterialParameters(E, K, N);
    }

    public static LoadingSequenceRequest request() {
        return new LoadingSequenceRequest(material(), KT, NOMINAL_STRESSES);
    }

    public static String renderTable(LoadingSequenceResult result, RambergOsgood law) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator())
                .append("内置算例: 圆角缺口 Kt=3, E=200000 MPa, K=1200 MPa, n=0.20 (单调加载)")
                .append(System.lineSeparator())
                .append(String.format(Locale.ROOT,
                        "比例极限(0.2%%残余应变) sigma_p = K*0.002^n ~= %.1f MPa",
                        law.proportionalLimitStress()))
                .append(System.lineSeparator())
                .append(" sigma_n | 弹性外推 Kt*sn | 真实应力 | 真实总应变 | 弹性应变 | 塑性应变 | 区间")
                .append(System.lineSeparator())
                .append("-----------------------------------------------------------------------------")
                .append(System.lineSeparator());
        for (var p : result.points()) {
            var pt = p.point();
            sb.append(String.format(Locale.ROOT,
                    " %7.1f | %14.1f | %8.2f | %9.6f | %9.6f | %9.6f | %s%n",
                    pt.nominalStress(), pt.elasticStress(), pt.trueStress(),
                    pt.trueTotalStrain(), pt.trueElasticStrain(), pt.truePlasticStrain(),
                    pt.regime()));
        }
        return sb.toString();
    }
}
