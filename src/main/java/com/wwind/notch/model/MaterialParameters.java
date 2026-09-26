package com.wwind.notch.model;

/**
 * Ramberg-Osgood monotonic material constants.
 *
 * @param elasticModulus E  (e.g. MPa)
 * @param strengthCoefficient K (same stress unit as E)
 * @param hardeningExponent n, dimensionless, 0 &lt; n &lt;= 1 typically
 */
public record MaterialParameters(double elasticModulus,
                                 double strengthCoefficient,
                                 double hardeningExponent) {
}
