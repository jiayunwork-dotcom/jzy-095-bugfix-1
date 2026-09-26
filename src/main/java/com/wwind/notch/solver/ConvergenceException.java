package com.wwind.notch.solver;

/**
 * Raised when the hand-written scalar root finder cannot guarantee a root.
 * Callers must surface this rather than returning a numerically suspicious value.
 */
public class ConvergenceException extends RuntimeException {

    public ConvergenceException(String message) {
        super(message);
    }
}
