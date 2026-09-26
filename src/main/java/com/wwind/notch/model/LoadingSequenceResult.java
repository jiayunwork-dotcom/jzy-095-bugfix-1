package com.wwind.notch.model;

import java.util.List;

/**
 * Batch result: every point is solved independently; order follows the request.
 */
public record LoadingSequenceResult(double kt,
                                    List<SequencePointResult> points) {
}
