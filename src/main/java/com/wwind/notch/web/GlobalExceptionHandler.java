package com.wwind.notch.web;

import com.wwind.notch.solver.ConvergenceException;
import com.wwind.notch.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates kernel exceptions to HTTP status codes:
 *  400 for invalid input (each violated rule carries its reason),
 *  422 for a root that fails to converge,
 *  400 for an unreadable/malformed JSON body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(ValidationException ex) {
        return ErrorResponse.of("VALIDATION_FAILED", ex.reasons());
    }

    @ExceptionHandler(ConvergenceException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleConvergence(ConvergenceException ex) {
        return ErrorResponse.of("NOT_CONVERGED", List.of(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadable(HttpMessageNotReadableException ex) {
        return ErrorResponse.of("MALFORMED_REQUEST", List.of("请求体不是合法 JSON 或字段类型错误"));
    }
}
