package com.wwind.notch.solver.sequence;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.LoadingSequenceResult;
import com.wwind.notch.model.NotchPointResult;
import com.wwind.notch.model.Segment;
import com.wwind.notch.model.SequencePointResult;
import com.wwind.notch.service.NotchAssessmentService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a nominal-stress sequence. Each point's Neuber/Ramberg-Osgood solution is
 * computed independently through {@link NotchAssessmentService}; nothing carries
 * numerical state between roots.
 *
 * The only cross-point logic is the deliberately thin unloading support: when a
 * later nominal stress drops below the running maximum it is marked UNLOADING and
 * local stress/strain hints are given by elastic rebound from that maximum. No
 * hysteresis loop, Bauschinger effect or reverse plasticity is tracked - this
 * kernel is monotonic by design.
 */
@Component
public class LoadingSequenceSolver {

    private final NotchAssessmentService assessmentService;

    public LoadingSequenceSolver(NotchAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    public LoadingSequenceResult solve(RambergOsgood law, double kt, List<Double> nominalStresses) {
        List<SequencePointResult> points = new ArrayList<>(nominalStresses.size());

        Double runningMax = null;
        double envelopeStress = 0.0;
        double envelopeStrain = 0.0;

        for (double nominalStress : nominalStresses) {
            NotchPointResult point = assessmentService.assessPoint(law, kt, nominalStress);

            Segment segment;
            double localStressHint;
            double localStrainHint;

            if (runningMax == null || nominalStress >= runningMax) {
                if (runningMax == null && nominalStress == 0.0) {
                    segment = Segment.NEUTRAL;
                } else {
                    segment = Segment.LOADING;
                }
                runningMax = (runningMax == null) ? nominalStress : Math.max(runningMax, nominalStress);
                envelopeStress = point.trueStress();
                envelopeStrain = point.trueTotalStrain();
                localStressHint = envelopeStress;
                localStrainHint = envelopeStrain;
            } else {
                segment = Segment.UNLOADING;
                // Elastic rebound from the running envelope maximum (thin layer only).
                double stressChange = kt * (nominalStress - runningMax);
                localStressHint = envelopeStress + stressChange;
                localStrainHint = envelopeStrain + stressChange / law.elasticModulus();
            }

            points.add(new SequencePointResult(point, segment, runningMax,
                    localStressHint, localStrainHint));
        }
        return new LoadingSequenceResult(kt, points);
    }
}
