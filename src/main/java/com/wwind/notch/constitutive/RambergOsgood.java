package com.wwind.notch.constitutive;

import com.wwind.notch.model.MaterialParameters;

/**
 * Monotonic Ramberg-Osgood constitutive law:
 * <pre>
 *     epsilon(sigma) = sigma / E + (sigma / K) ^ (1/n)
 * </pre>
 * The sign convention is odd-symmetric, so compression loads map through the
 * tension envelope by sign folding. This class holds the law only; it knows
 * nothing about Neuber or HTTP.
 *
 * CRITICAL: the plastic exponent is 1/n. Writing n there instead tilts the whole
 * plastic branch, so the exponent is derived in exactly one place:
 * {@link #plasticExponent()}.
 */
public class RambergOsgood {

    /** Engineering strain used to define the proportional limit (0.2% offset). */
    public static final double OFFSET_STRAIN = 0.002;

    private final double elasticModulus;
    private final double strengthCoefficient;
    private final double hardeningExponent;

    public RambergOsgood(MaterialParameters material) {
        this(material.elasticModulus(),
                material.strengthCoefficient(),
                material.hardeningExponent());
    }

    public RambergOsgood(double elasticModulus, double strengthCoefficient, double hardeningExponent) {
        if (!(elasticModulus > 0.0)) {
            throw new IllegalArgumentException("弹性模量 E 必须为正数");
        }
        if (!(strengthCoefficient > 0.0)) {
            throw new IllegalArgumentException("强度系数 K 必须为正数");
        }
        if (!(hardeningExponent > 0.0)) {
            throw new IllegalArgumentException("硬化指数 n 必须为正数");
        }
        this.elasticModulus = elasticModulus;
        this.strengthCoefficient = strengthCoefficient;
        this.hardeningExponent = hardeningExponent;
    }

    public double elasticModulus() {
        return elasticModulus;
    }

    public double strengthCoefficient() {
        return strengthCoefficient;
    }

    public double hardeningExponent() {
        return hardeningExponent;
    }

    /** The plastic power 1/n — single source of truth for the exponent. */
    public double plasticExponent() {
        return 1.0 / hardeningExponent;
    }

    /** Total strain (odd-symmetric in stress). */
    public double totalStrain(double stress) {
        return elasticStrain(stress) + plasticStrain(stress);
    }

    /** Hooke part sigma / E. */
    public double elasticStrain(double stress) {
        return stress / elasticModulus;
    }

    /** Plastic part (|sigma| / K)^(1/n), sign folded onto sigma. */
    public double plasticStrain(double stress) {
        double magnitude = Math.abs(stress);
        double signedMagnitude = Math.pow(magnitude / strengthCoefficient, plasticExponent());
        return Math.copySign(signedMagnitude, stress);
    }

    /**
     * Proportional limit defined by the 0.2% residual-strain (offset) convention,
     * i.e. the stress at which (sigma/K)^(1/n) = 0.002.
     * Solved analytically: sigma = K * 0.002^n.
     */
    public double proportionalLimitStress() {
        return strengthCoefficient * Math.pow(OFFSET_STRAIN, hardeningExponent);
    }
}
