package com.wwind.notch.solver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BisectionRootFinderTest {

    private final BisectionRootFinder finder = new BisectionRootFinder();

    @Test
    void findsRootOfLinearFunction() {
        double root = finder.findRoot(x -> x - 3.0, 0.0, 10.0);
        assertEquals(3.0, root, 1e-10);
    }

    @Test
    void findsRootOfQuadratic() {
        double root = finder.findRoot(x -> x * x - 2.0, 0.0, 2.0);
        assertEquals(Math.sqrt(2.0), root, 1e-12);
    }

    @Test
    void rootOnBracketEndpointIsReturned() {
        assertEquals(2.0, finder.findRoot(x -> (x - 2.0), 2.0, 5.0), 0.0);
    }

    @Test
    void sameSignEndpointsRaiseConvergenceError() {
        ConvergenceException ex = assertThrows(ConvergenceException.class,
                () -> finder.findRoot(x -> x * x + 1.0, -1.0, 1.0));
        assertTrue(ex.getMessage().contains("同号"));
    }

    @Test
    void exhaustedIterationBudgetRaisesConvergenceError() {
        // A deliberately tiny budget with a huge interval cannot converge.
        RootFinder slow = new BisectionRootFinder(3, 1e-14, 1e-14);
        assertThrows(ConvergenceException.class, () -> slow.findRoot(x -> x - 1.0, 0.0, 1e12));
    }

    @Test
    void illegalBracketRaisesConvergenceError() {
        assertThrows(ConvergenceException.class,
                () -> finder.findRoot(x -> x, 1.0, 1.0));
    }
}
