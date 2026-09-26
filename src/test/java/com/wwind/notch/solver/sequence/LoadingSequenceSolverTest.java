package com.wwind.notch.solver.sequence;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.LoadingSequenceResult;
import com.wwind.notch.model.Regime;
import com.wwind.notch.model.Segment;
import com.wwind.notch.solver.BisectionRootFinder;
import com.wwind.notch.solver.neuber.NeuberPointSolver;
import com.wwind.notch.service.NotchAssessmentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingSequenceSolverTest {

    private final RambergOsgood law = new RambergOsgood(200_000, 1_200, 0.2);
    private final LoadingSequenceSolver solver = new LoadingSequenceSolver(
            new NotchAssessmentService(new NeuberPointSolver(new BisectionRootFinder())));

    @Test
    void increasingSequenceTracesElasticToPlasticTransitionIndependently() {
        LoadingSequenceResult result = solver.solve(law, 3.0, List.of(50.0, 100.0, 120.0, 400.0));

        assertEquals(4, result.points().size());
        assertEquals(Regime.ELASTIC, result.points().get(0).point().regime());
        assertEquals(Regime.PLASTIC, result.points().get(3).point().regime());
        assertTrue(result.points().stream().allMatch(p -> p.segment() == Segment.LOADING));

        // Each point equals an independent single-point solve.
        assertEquals(150.0, result.points().get(0).point().trueStress(), 0.0);
        assertEquals(1200.0, result.points().get(3).point().elasticStress(), 0.0);
        assertTrue(result.points().get(3).point().trueStress() < 1200.0);
    }

    @Test
    void firstZeroIsNeutralAndPositivePointsAreLoading() {
        LoadingSequenceResult result = solver.solve(law, 3.0, List.of(0.0, 100.0));
        assertEquals(Segment.NEUTRAL, result.points().get(0).segment());
        assertEquals(Segment.LOADING, result.points().get(1).segment());
    }

    @Test
    void decreasingPointIsMarkedUnloadingWithElasticReboundHint() {
        LoadingSequenceResult result = solver.solve(law, 3.0, List.of(400.0, 300.0));

        assertEquals(Segment.LOADING, result.points().get(0).segment());
        assertEquals(Segment.UNLOADING, result.points().get(1).segment());

        var envelope = result.points().get(0);
        var unload = result.points().get(1);
        // Thin elastic layer: stress drops by Kt * delta sigma_n.
        double expectedStress = envelope.localStressHint() + 3.0 * (300.0 - 400.0);
        assertEquals(expectedStress, unload.localStressHint(), 1e-9);
        double expectedStrain = envelope.localStrainHint() + 3.0 * (-100.0) / 200_000.0;
        assertEquals(expectedStrain, unload.localStrainHint(), 1e-12);
        // The independent point result itself is still the monotonic coupled value.
        assertEquals(Regime.PLASTIC, unload.point().regime());
    }
}
