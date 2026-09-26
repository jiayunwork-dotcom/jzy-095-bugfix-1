package com.wwind.notch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.wwind.notch.solver.BisectionRootFinder;
import com.wwind.notch.solver.ConvergenceException;
import com.wwind.notch.solver.RootFinder;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real HTTP stack with a deliberately failing RootFinder bean to prove
 * that a non-converging plastic root is reported as 422 with a reason, instead of
 * leaking a suspicious number.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConvergenceHttpPathTest {

    @Autowired
    private MockMvc mvc;

    @TestConfiguration
    static class BrokenFinderConfig {
        @Bean
        @Primary
        RootFinder brokenRootFinder() {
            return new RootFinder() {
                @Override
                public double findRoot(java.util.function.DoubleUnaryOperator function,
                                       double lower, double upper) {
                    throw new ConvergenceException("测试注入: 求根迭代不收敛");
                }
            };
        }
    }

    @Test
    void nonConvergingPlasticRootReturns422() throws Exception {
        String body = """
                {
                  "material": {"elasticModulus": 200000, "strengthCoefficient": 1200, "hardeningExponent": 0.2},
                  "kt": 3.0,
                  "nominalStress": 400.0
                }
                """;
        mvc.perform(post("/api/notch/assess").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("NOT_CONVERGED"))
                .andExpect(jsonPath("$.reasons[0]").value(
                        org.hamcrest.Matchers.containsString("不收敛")));
    }
}
