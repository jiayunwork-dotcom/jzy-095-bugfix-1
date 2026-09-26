package com.wwind.notch.validation;

import com.wwind.notch.model.MaterialParameters;
import com.wwind.notch.model.NotchAssessmentRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputValidatorTest {

    private final InputValidator validator = new InputValidator();

    private final MaterialParameters good = new MaterialParameters(200_000, 1_200, 0.2);

    @Test
    void acceptsValidRequest() {
        validator.validate(new NotchAssessmentRequest(good, 3.0, 300.0));
    }

    @Test
    void nonPositiveElasticModulusIsRejectedWithReason() {
        var ex = assertThrows(ValidationException.class, () -> validator.validate(
                new NotchAssessmentRequest(new MaterialParameters(0, 1200, 0.2), 3, 100)));
        assertTrue(ex.reasons().stream().anyMatch(r -> r.contains("弹性模量")));
    }

    @Test
    void nonPositiveStrengthCoefficientIsRejectedWithReason() {
        var ex = assertThrows(ValidationException.class, () -> validator.validate(
                new NotchAssessmentRequest(new MaterialParameters(200_000, -5, 0.2), 3, 100)));
        assertTrue(ex.reasons().stream().anyMatch(r -> r.contains("强度系数")));
    }

    @Test
    void nonPositiveHardeningExponentIsRejectedWithReason() {
        var ex = assertThrows(ValidationException.class, () -> validator.validate(
                new NotchAssessmentRequest(new MaterialParameters(200_000, 1200, -0.1), 3, 100)));
        assertTrue(ex.reasons().stream().anyMatch(r -> r.contains("硬化指数")));
    }

    @Test
    void ktBelowOneIsRejectedWithReason() {
        var ex = assertThrows(ValidationException.class, () -> validator.validate(
                new NotchAssessmentRequest(good, 0.9, 100)));
        assertTrue(ex.reasons().stream().anyMatch(r -> r.contains("应力集中系数")));
    }

    @Test
    void ktExactlyOneIsAccepted() {
        validator.validate(new NotchAssessmentRequest(good, 1.0, 100));
    }

    @Test
    void missingMaterialObjectIsRejected() {
        var ex = assertThrows(ValidationException.class, () -> validator.validate(
                new NotchAssessmentRequest(null, 3, 100)));
        assertTrue(!ex.reasons().isEmpty());
    }

    @Test
    void nullRequestIsRejected() {
        assertThrows(ValidationException.class, () -> validator.validate((com.wwind.notch.model.NotchAssessmentRequest) null));
    }

    @Test
    void batchRejectsEmptyOrNullSequence() {
        assertThrows(ValidationException.class, () -> validator.validate(
                new com.wwind.notch.model.LoadingSequenceRequest(good, 3, null)));
        assertThrows(ValidationException.class, () -> validator.validate(
                new com.wwind.notch.model.LoadingSequenceRequest(good, 3, List.of())));
    }

    @Test
    void batchRejectsNonFiniteAndNullEntries() {
        var ex = assertThrows(ValidationException.class, () -> validator.validate(
                new com.wwind.notch.model.LoadingSequenceRequest(good, 3,
                        java.util.Arrays.asList(100.0, null, Double.NaN))));
        assertTrue(ex.reasons().size() >= 2);
    }

    @Test
    void collectsMultipleReasonsAtOnce() {
        var ex = assertThrows(ValidationException.class, () -> validator.validate(
                new NotchAssessmentRequest(new MaterialParameters(-1, 0, 0), 0.5, 100)));
        assertTrue(ex.reasons().size() >= 4, "应一次报出全部违规原因，实际: " + ex.reasons());
    }
}
