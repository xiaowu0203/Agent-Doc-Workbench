package com.agentdoc.agent.execution.tool;

import com.agentdoc.agent.constant.McpConstant;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespacedToolCallbackTest {

    @Test
    void exposesNamespacedNameAndDelegatesCall() {
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("search").description("Search")
                        .inputSchema("{\"type\":\"object\"}").build();
            }
            @Override public String call(String input) { return "result:" + input; }
        };

        NamespacedToolCallback callback = new NamespacedToolCallback(delegate, "web-search");

        assertThat(callback.getToolDefinition().name()).isEqualTo("web-search__search");
        assertThat(callback.call("query")).isEqualTo("result:query");
    }

    @Test
    void rejectsOversizedToolResult() {
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("demo").description("Demo")
                        .inputSchema("{\"type\":\"object\"}").build();
            }
            @Override public String call(String input) { return "123456"; }
        };

        assertThatThrownBy(() -> new ToolResultSizeLimitCallback(delegate, 5).call("{}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsOversizedNamespacedToolName() {
        ToolCallback delegate = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("tool").description("Demo")
                        .inputSchema("{\"type\":\"object\"}").build();
            }
            @Override public String call(String input) { return input; }
        };

        assertThatThrownBy(() -> new NamespacedToolCallback(delegate,
                "s".repeat(McpConstant.MAX_MODEL_TOOL_NAME_LENGTH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工具名超过长度限制");
    }
}
