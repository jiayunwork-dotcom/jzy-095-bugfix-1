package com.wwind.notch.model;

/**
 * Notch root result for one nominal stress, with the elastic extrapolation shown
 * side by side for comparison.
 *
 * @param nominalStress    sigma_n
 * @param kt               stress concentration factor used
 * @param elasticStress    Kt * sigma_n (what pure elasticity would predict)
 * @param elasticStrain    Kt * sigma_n / E
 * @param trueStress       coupled Neuber + R-O root stress sigma
 * @param trueTotalStrain  coupled total strain epsilon = sigma/E + (sigma/K)^(1/n)
 * @param trueElasticStrain sigma / E part of the total strain
 * @param truePlasticStrain (sigma/K)^(1/n) part of the total strain
 * @param regime           ELASTIC or PLASTIC
 */
public record NotchPointResult(double nominalStress,
                               double kt,
                               double elasticStress,
                               double elasticStrain,
                               double trueStress,
                               double trueTotalStrain,
                               double trueElasticStrain,
                               double truePlasticStrain,
                               Regime regime) {
}
