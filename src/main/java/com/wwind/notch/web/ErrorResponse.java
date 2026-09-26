package com.wwind.notch.web;

import java.util.List;

/**
 * Uniform JSON error body.
 */
public record ErrorResponse(String error, List<String> reasons) {

    public static ErrorResponse of(String error, List<String> reasons) {
        return new ErrorResponse(error, reasons);
    }
}
