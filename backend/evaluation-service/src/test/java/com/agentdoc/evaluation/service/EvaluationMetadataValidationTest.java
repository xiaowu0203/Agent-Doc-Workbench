package com.agentdoc.evaluation.service;

import com.agentdoc.evaluation.pojo.dto.DatasetCaseBindingDTO;
import com.agentdoc.evaluation.pojo.dto.DatasetCaseBindingsDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationRunCreateDTO;
import com.agentdoc.evaluation.pojo.dto.ExperimentCreateDTO;
import com.agentdoc.evaluation.pojo.dto.PromptCandidateCreateDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluationMetadataValidationTest {
    @Test
    void optionalNamesKeepThePublicLimitAndBindingLimitIncludesDisabledItems() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var name : List.of("", " ", "名".repeat(200))) {
                assertThat(validator.validate(new EvaluationRunCreateDTO(9L, 10L, null, 600L, name))).isEmpty();
                assertThat(validator.validate(new ExperimentCreateDTO(9L, "key", 10L,
                        List.of(new PromptCandidateCreateDTO("candidate", "prompt")), name))).isEmpty();
            }
            assertThat(validator.validate(new EvaluationRunCreateDTO(9L, 10L, null, 600L, "名".repeat(201))))
                    .anyMatch(issue -> issue.getPropertyPath().toString().equals("name"));
            assertThat(validator.validate(new ExperimentCreateDTO(9L, "key", 10L,
                    List.of(new PromptCandidateCreateDTO("candidate", "prompt")), "名".repeat(201))))
                    .anyMatch(issue -> issue.getPropertyPath().toString().equals("name"));
            var bindings = IntStream.rangeClosed(1, 1000).mapToObj(id -> new DatasetCaseBindingDTO((long) id, id, false)).toList();
            assertThat(validator.validate(new DatasetCaseBindingsDTO(bindings))).isEmpty();
            var oversized = IntStream.rangeClosed(1, 1001).mapToObj(id -> new DatasetCaseBindingDTO((long) id, id, false)).toList();
            assertThat(validator.validate(new DatasetCaseBindingsDTO(oversized))).isNotEmpty();
        }
    }
}
