package com.wwind.notch.example;

import com.wwind.notch.constitutive.RambergOsgood;
import com.wwind.notch.model.LoadingSequenceRequest;
import com.wwind.notch.model.LoadingSequenceResult;
import com.wwind.notch.solver.sequence.LoadingSequenceSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads and prints the built-in Kt=3 example once the service has started, showing
 * the coupled elastic-plastic results against the elastic extrapolation.
 */
@Component
public class ExampleRunner {

    private static final Logger log = LoggerFactory.getLogger(ExampleRunner.class);

    private final LoadingSequenceSolver sequenceSolver;

    public ExampleRunner(LoadingSequenceSolver sequenceSolver) {
        this.sequenceSolver = sequenceSolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void printBuiltInExample() {
        try {
            LoadingSequenceRequest request = BuiltInExample.request();
            RambergOsgood law = new RambergOsgood(request.material());
            LoadingSequenceResult result = sequenceSolver.solve(law, request.kt(), request.nominalStresses());
            log.info(BuiltInExample.renderTable(result));
        } catch (RuntimeException ex) {
            // Printing the demo must never prevent the HTTP service from serving.
            log.warn("内置算例打印失败: {}", ex.getMessage());
        }
    }
}
