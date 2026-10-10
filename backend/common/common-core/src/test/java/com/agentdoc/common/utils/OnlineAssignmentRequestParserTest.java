package com.agentdoc.common.utils;

import com.agentdoc.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OnlineAssignmentRequestParserTest {
    private final String valid = "{\"taskId\":\"10\",\"spaceId\":\"20\",\"agentId\":\"30\",\"documentId\":\"40\",\"actorId\":\"50\",\"requestKey\":\"key\","
            + "\"requestHash\":\"a\",\"inputHash\":\"b\",\"documentVersion\":\"1\",\"documentContentHash\":\"c\",\"tokenBudget\":\"100\",\"inputSchemaVersion\":1}";
    @Test void rejectsCoercedDecimalIdentityBudgetDuplicateAndTrailingValues() {
        assertThat(OnlineAssignmentRequestParser.parse(valid).taskId()).isEqualTo("10");
        for (String invalid : List.of(valid.replace("\"10\"", "10"), valid.replace("\"100\"", "100"), valid.replace("\"inputSchemaVersion\":1", "\"inputSchemaVersion\":\"1\""),
                valid.replace("\"taskId\":\"10\"", "\"taskId\":\"10\",\"taskId\":\"11\""), valid + "{}")) {
            assertThatThrownBy(() -> OnlineAssignmentRequestParser.parse(invalid)).isInstanceOf(BusinessException.class);
        }
    }
}
