package com.wwind.notch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.wwind.notch.service.NotchAssessmentService;
import com.wwind.notch.solver.BisectionRootFinder;
import com.wwind.notch.solver.RootFinder;
import com.wwind.notch.solver.neuber.NeuberPointSolver;
import com.wwind.notch.solver.sequence.LoadingSequenceSolver;
import com.wwind.notch.validation.InputValidator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotchController.class)
@Import({InputValidator.class, NotchAssessmentService.class, NeuberPointSolver.class,
        LoadingSequenceSolver.class, GlobalExceptionHandler.class})
class NotchControllerTest {

    @Autowired
    private MockMvc mvc;

    @TestConfiguration
    static class FinderConfig {
        @Bean
        RootFinder rootFinder() {
            return new BisectionRootFinder();
        }
    }

    private static final String ELASTIC_BODY = """
            {
              "material": {"elasticModulus": 200000, "strengthCoefficient": 1200, "hardeningExponent": 0.2},
              "kt": 3.0,
              "nominalStress": 100.0
            }
            """;

    @Test
    void singlePointElasticReturnsExactKtTimesNominal() throws Exception {
        mvc.perform(post("/api/notch/assess").contentType(MediaType.APPLICATION_JSON).content(ELASTIC_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trueStress").value(300.0))
                .andExpect(jsonPath("$.elasticStress").value(300.0))
                .andExpect(jsonPath("$.regime").value("ELASTIC"));
    }

    @Test
    void plasticPointShowsTrueStressBelowElasticExtrapolation() throws Exception {
        String body = ELASTIC_BODY.replace("100.0\n", "400.0\n");
        byte[] response = mvc.perform(post("/api/notch/assess")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regime").value("PLASTIC"))
                .andExpect(jsonPath("$.elasticStress").value(1200.0))
                .andReturn().getResponse().getContentAsByteArray();

        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        org.junit.jupiter.api.Assertions.assertTrue(json.get("trueStress").asDouble() < 1200.0,
                "塑性后真实应力必须低于弹性外推");
        org.junit.jupiter.api.Assertions.assertTrue(json.get("truePlasticStrain").asDouble() > 0.0,
                "塑性分量必须非零");
        org.junit.jupiter.api.Assertions.assertTrue(
                json.get("trueTotalStrain").asDouble() > 1200.0 / 200_000.0,
                "塑性后真实应变必须高于弹性应变");
    }

    @Test
    void invalidInputReturns400WithReasons() throws Exception {
        String bad = """
                {
                  "material": {"elasticModulus": -1, "strengthCoefficient": 0, "hardeningExponent": 0},
                  "kt": 0.5,
                  "nominalStress": 100
                }
                """;
        mvc.perform(post("/api/notch/assess").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.reasons.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/notch/assess").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void sequenceEndpointReturnsAllPoints() throws Exception {
        String body = """
                {
                  "material": {"elasticModulus": 200000, "strengthCoefficient": 1200, "hardeningExponent": 0.2},
                  "kt": 3.0,
                  "nominalStresses": [100.0, 400.0]
                }
                """;
        mvc.perform(post("/api/notch/sequence").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].point.regime").value("ELASTIC"))
                .andExpect(jsonPath("$.points[1].point.regime").value("PLASTIC"))
                .andExpect(jsonPath("$.points[1].segment").value("LOADING"));
    }

    @Test
    void builtInExampleIsReachableAndCrossesIntoPlasticity() throws Exception {
        byte[] response = mvc.perform(get("/api/notch/example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kt").value(3.0))
                .andExpect(jsonPath("$.points.length()").value(10))
                .andExpect(jsonPath("$.points[0].point.regime").value("ELASTIC"))
                .andExpect(jsonPath("$.points[9].point.regime").value("PLASTIC"))
                .andReturn().getResponse().getContentAsByteArray();

        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        org.junit.jupiter.api.Assertions.assertTrue(
                json.get("points").get(9).get("point").get("trueStress").asDouble() < 1200.0,
                "内置算例塑性点真实应力必须低于 Kt*sigma_n=1200");
    }
}
