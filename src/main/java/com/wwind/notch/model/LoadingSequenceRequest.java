package com.wwind.notch.model;

import java.util.List;

/**
 * Batch request over an ordered nominal stress sequence (typically monotonically
 * increasing, so that the elastic -> plastic transition can be traced).
 */
public record LoadingSequenceRequest(MaterialParameters material,
                                     double kt,
                                     List<Double> nominalStresses) {
}
