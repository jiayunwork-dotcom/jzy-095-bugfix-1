package com.wwind.notch.solver;

import org.springframework.stereotype.Component;

import java.util.function.DoubleUnaryOperator;

/**
 * Bisection root finder. Robust for the monotone continuous residual produced by
 * the Neuber/Ramberg-Osgood coupling: bisection cannot drift, cannot miss a
 * bracketed root, and reports failure honestly instead of returning a guess.
 */
@Component
public class BisectionRootFinder implements RootFinder {

    private static final int DEFAULT_MAX_ITERATIONS = 100;
    private static final double DEFAULT_REL_TOLERANCE = 1.0e-14;
    private static final double DEFAULT_ABS_TOLERANCE = 1.0e-14;

    private final int maxIterations;
    private final double relTolerance;
    private final double absTolerance;

    public BisectionRootFinder() {
        this(DEFAULT_MAX_ITERATIONS, DEFAULT_REL_TOLERANCE, DEFAULT_ABS_TOLERANCE);
    }

    public BisectionRootFinder(int maxIterations, double relTolerance, double absTolerance) {
        this.maxIterations = maxIterations;
        this.relTolerance = relTolerance;
        this.absTolerance = absTolerance;
    }

    @Override
    public double findRoot(DoubleUnaryOperator function, double lower, double upper) {
        if (lower >= upper) {
            throw new ConvergenceException(
                    "求根区间非法: 下界 " + lower + " 必须严格小于上界 " + upper);
        }

        double fLow = function.applyAsDouble(lower);
        double fHigh = function.applyAsDouble(upper);
        if (!Double.isFinite(fLow) || !Double.isFinite(fHigh)) {
            throw new ConvergenceException("求根区间端点上的残差非有限值，无法开始二分");
        }
        if (fLow == 0.0) {
            return lower;
        }
        if (fHigh == 0.0) {
            return upper;
        }
        if (Math.signum(fLow) == Math.signum(fHigh)) {
            throw new ConvergenceException(String.format(
                    "求根失败: 区间 [%.6g, %.6g] 两端残差同号 (f=%.6g, %.6g)，根未被夹住",
                    lower, upper, fLow, fHigh));
        }

        double a = lower;
        double b = upper;
        double fa = fLow;

        for (int i = 0; i < maxIterations; i++) {
            double mid = 0.5 * (a + b);
            double fm = function.applyAsDouble(mid);
            if (!Double.isFinite(fm)) {
                throw new ConvergenceException("求根过程中残差变为非有限值（第 " + (i + 1) + " 次迭代）");
            }
            if (fm == 0.0 || converged(a, b, mid)) {
                return mid;
            }
            if (Math.signum(fa) == Math.signum(fm)) {
                a = mid;
                fa = fm;
            } else {
                b = mid;
            }
        }
        throw new ConvergenceException("求根失败: 二分 " + maxIterations
                + " 次后仍未达到容差，拒绝返回可疑数值");
    }

    private boolean converged(double a, double b, double mid) {
        double width = b - a;
        return width <= absTolerance || width <= relTolerance * Math.max(1.0, Math.abs(mid));
    }
}
