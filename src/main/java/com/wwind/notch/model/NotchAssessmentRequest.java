package com.wwind.notch.model;

import java.util.List;

/**
 * Single nominal stress assessment request.
 *
 * @param material E/K/n constants
 * @param kt elastic stress concentration factor (>= 1)
 * @param nominalStress nominal (net-section) stress in the same unit as K
 */
public record NotchAssessmentRequest(MaterialParameters material,
                                     double kt,
                                     double nominalStress) {
}
