package com.wwind.notch.web;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.example.BuiltInExample;
import com.wwind.notch.model.LoadingSequenceRequest;
import com.wwind.notch.model.LoadingSequenceResult;
import com.wwind.notch.model.NotchAssessmentRequest;
import com.wwind.notch.model.NotchPointResult;
import com.wwind.notch.service.NotchAssessmentService;
import com.wwind.notch.solver.sequence.LoadingSequenceSolver;
import com.wwind.notch.validation.InputValidator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface. JSON in, JSON out; no web UI.
 *
 *  POST /api/notch/assess           single nominal stress
 *  POST /api/notch/sequence         increasing nominal stress sequence
 *  GET  /api/notch/example          the built-in Kt=3 rounded-notch example
 */
@RestController
@RequestMapping("/api/notch")
public class NotchController {

    private final InputValidator validator;
    private final NotchAssessmentService assessmentService;
    private final LoadingSequenceSolver sequenceSolver;

    public NotchController(InputValidator validator,
                           NotchAssessmentService assessmentService,
                           LoadingSequenceSolver sequenceSolver) {
        this.validator = validator;
        this.assessmentService = assessmentService;
        this.sequenceSolver = sequenceSolver;
    }

    @PostMapping("/assess")
    public NotchPointResult assess(@RequestBody NotchAssessmentRequest request) {
        validator.validate(request);
        return assessmentService.assess(request.material(), request.kt(), request.nominalStress());
    }

    @PostMapping("/sequence")
    public LoadingSequenceResult sequence(@RequestBody LoadingSequenceRequest request) {
        validator.validate(request);
        RambergOsgood law = new RambergOsgood(request.material());
        return sequenceSolver.solve(law, request.kt(), request.nominalStresses());
    }

    @GetMapping("/example")
    public LoadingSequenceResult example() {
        var request = BuiltInExample.request();
        RambergOsgood law = new RambergOsgood(request.material());
        return sequenceSolver.solve(law, request.kt(), request.nominalStresses());
    }
}
