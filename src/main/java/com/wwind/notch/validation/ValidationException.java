package com.wwind.notch.validation;

import java.util.List;

/**
 * Thrown when request inputs fail validation. Every violated rule contributes a
 * human-readable reason so callers see all problems in one response.
 */
public class ValidationException extends RuntimeException {

    private final transient List<String> reasons;

    public ValidationException(List<String> reasons) {
        super("输入校验失败: " + String.join("; ", reasons));
        this.reasons = List.copyOf(reasons);
    }

    public List<String> reasons() {
        return reasons;
    }
}
