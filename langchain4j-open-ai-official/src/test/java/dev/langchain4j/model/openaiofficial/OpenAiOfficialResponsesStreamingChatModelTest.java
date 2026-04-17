package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.ResponseComputerToolCall;
import com.openai.models.responses.ResponseFileSearchToolCall;
import com.openai.models.responses.ResponseFunctionShellToolCall;
import com.openai.models.responses.ResponseFunctionShellToolCallOutput;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseFunctionWebSearch;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseToolSearchCall;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiOfficialResponsesStreamingChatModelTest {

    @Test
    void should_convert_legacy_web_search_domain_attributes_into_filters_only() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("web_search")
                .addAttribute("allowed_domains", List.of("openai.com"))
                .addAttribute("blocked_domains", List.of("example.com"))
                .addAttribute("latency_tier", "interactive")
                .build();

        var tool = OpenAiOfficialServerToolMapper.toResponsesTool(serverTool);

        assertThat(tool.isWebSearch()).isTrue();
        assertThat(tool.asWebSearch().filters()).isPresent();
        assertThat(tool.asWebSearch().filters().get().allowedDomains()).hasValue(List.of("openai.com"));
        assertThat(tool.asWebSearch().filters().get()._additionalProperties())
                .containsEntry("blocked_domains", JsonValue.from(List.of("example.com")));
        assertThat(tool.asWebSearch()._additionalProperties())
                .containsKey("latency_tier")
                .doesNotContainKeys("allowed_domains", "blocked_domains");
    }

    @Test
    void should_keep_backward_compatible_tool_search_attributes_api() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("tool_search")
                .addAttribute("description", "Search tools")
                .addAttribute("execution", "required")
                .build();

        var tool = OpenAiOfficialServerToolMapper.toResponsesTool(serverTool);

        assertThat(tool.isSearch()).isTrue();
        assertThat(tool.asSearch().description()).hasValue("Search tools");
        assertThat(tool.asSearch().execution())
                .hasValueSatisfying(value -> assertThat(value.asString()).isEqualTo("required"));
    }

    @Test
    void should_preserve_namespace_function_additional_properties() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("namespace")
                .name("crm")
                .addAttribute("description", "CRM tools")
                .addAttribute(
                        "tools",
                        List.of(Map.of(
                                "type", "function",
                                "name", "list_open_orders",
                                "description", "List open orders for a customer ID.",
                                "defer_loading", true,
                                "parameters",
                                        Map.of(
                                                "type", "object",
                                                "properties", Map.of("customer_id", Map.of("type", "string")),
                                                "required", List.of("customer_id")))))
                .build();

        var tool = OpenAiOfficialServerToolMapper.toResponsesTool(serverTool);

        assertThat(tool.isNamespace()).isTrue();
        assertThat(tool.asNamespace().tools()).hasSize(1);
        assertThat(tool.asNamespace().tools().get(0).isFunction()).isTrue();
        assertThat(tool.asNamespace().tools().get(0).asFunction()._additionalProperties())
                .containsEntry("defer_loading", JsonValue.from(true));
    }

    @Test
    void should_reject_namespace_custom_tool() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("namespace")
                .name("github")
                .addAttribute("description", "GitHub tools")
                .addAttribute(
                        "tools",
                        List.of(Map.of(
                                "type", "custom",
                                "name", "search_code",
                                "description", "Search code",
                                "defer_loading", true)))
                .build();

        assertThatThrownBy(() -> OpenAiOfficialServerToolMapper.toResponsesTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported namespace nested tool type: custom");
    }

    @Test
    void should_reject_unsupported_shell_environment_type() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of("type", "remote"))
                .build();

        assertThatThrownBy(() -> OpenAiOfficialServerToolMapper.toResponsesTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported shell environment type: remote");
    }

    @Test
    void should_reject_unsupported_shell_container_skill_type() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute(
                        "environment", Map.of("type", "container_auto", "skills", List.of(Map.of("type", "custom"))))
                .build();

        assertThatThrownBy(() -> OpenAiOfficialServerToolMapper.toResponsesTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported shell container skill type: custom");
    }

    @Test
    void should_reject_unsupported_shell_container_network_policy_type() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute(
                        "environment", Map.of("type", "container_auto", "network_policy", Map.of("type", "restricted")))
                .build();

        assertThatThrownBy(() -> OpenAiOfficialServerToolMapper.toResponsesTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported shell container network_policy type: restricted");
    }

    @Test
    void should_reject_web_search_preview_server_tool() {
        OpenAiOfficialServerTool serverTool =
                OpenAiOfficialServerTool.builder().type("web_search_preview").build();

        assertThatThrownBy(() -> OpenAiOfficialServerToolMapper.toResponsesTool(serverTool))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining(
                        "Supported types are: web_search, file_search, tool_search, mcp, shell, computer, namespace.");
    }

    @Test
    void should_extract_shell_and_computer_server_tool_results_only() {
        ResponseOutputItem functionCall = ResponseOutputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
                .id("fn_1")
                .callId("call_1")
                .name("weather")
                .arguments("{}")
                .status(ResponseFunctionToolCall.Status.COMPLETED)
                .type(JsonValue.from("function_call"))
                .build());

        ResponseOutputItem shellCall = ResponseOutputItem.ofShellCall(ResponseFunctionShellToolCall.builder()
                .id("shell_1")
                .callId("shell_call_1")
                .action(ResponseFunctionShellToolCall.Action.builder()
                        .addCommand("ls")
                        .maxOutputLength(1024)
                        .timeoutMs(1000)
                        .build())
                .environment(com.openai.models.responses.ResponseLocalEnvironment.builder()
                        .type(JsonValue.from("local"))
                        .build())
                .status(ResponseFunctionShellToolCall.Status.COMPLETED)
                .type(JsonValue.from("shell_call"))
                .build());

        ResponseOutputItem computerCall = ResponseOutputItem.ofComputerCall(ResponseComputerToolCall.builder()
                .id("computer_1")
                .callId("computer_call_1")
                .pendingSafetyChecks(List.of())
                .status(ResponseComputerToolCall.Status.COMPLETED)
                .type(ResponseComputerToolCall.Type.COMPUTER_CALL)
                .action(ResponseComputerToolCall.Action.Click.builder()
                        .button(ResponseComputerToolCall.Action.Click.Button.LEFT)
                        .type(JsonValue.from("click"))
                        .x(10)
                        .y(20)
                        .build())
                .build());

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(
                        List.of(functionCall, shellCall, computerCall));

        assertThat(results)
                .extracting(OpenAiOfficialServerToolResult::type)
                .containsExactly("shell_call", "computer_call");
    }

    @Test
    void should_extract_computer_server_tool_results_without_pending_safety_checks() {
        ResponseOutputItem computerCall;
        try {
            computerCall = ObjectMappers.jsonMapper().readValue("""
                    {
                      "id": "computer_2",
                      "call_id": "computer_call_2",
                      "status": "completed",
                      "type": "computer_call",
                      "action": {
                        "type": "click",
                        "button": "left",
                        "x": 10,
                        "y": 20
                      }
                    }
                    """, ResponseOutputItem.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(List.of(computerCall));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo("computer_call");
        assertThat(((Map<String, Object>) results.get(0).content()).keySet())
                .contains("id", "call_id", "status", "type", "action")
                .doesNotContain("pending_safety_checks");
    }

    @Test
    void should_extract_shell_call_output_server_tool_results() {
        ResponseOutputItem shellCallOutput =
                ResponseOutputItem.ofShellCallOutput(ResponseFunctionShellToolCallOutput.builder()
                        .id("shell_out_1")
                        .callId("shell_call_1")
                        .maxOutputLength(1024)
                        .addOutput(ResponseFunctionShellToolCallOutput.Output.builder()
                                .stdout("hello")
                                .stderr("")
                                .exitOutcome(0)
                                .build())
                        .status(ResponseFunctionShellToolCallOutput.Status.COMPLETED)
                        .type(JsonValue.from("shell_call_output"))
                        .build());

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(List.of(shellCallOutput));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo("shell_call_output");
        assertThat(results.get(0).toolUseId()).isEqualTo("shell_out_1");
        assertThat(((Map<String, Object>) results.get(0).content()).keySet())
                .contains("id", "call_id", "output", "status", "max_output_length");
    }

    @Test
    void should_extract_web_file_tool_search_and_mcp_server_tool_results() {
        ResponseOutputItem webSearchCall = ResponseOutputItem.ofWebSearchCall(ResponseFunctionWebSearch.builder()
                .id("web_1")
                .action(ResponseFunctionWebSearch.Action.Search.builder()
                        .query("latest ai news")
                        .build())
                .status(ResponseFunctionWebSearch.Status.COMPLETED)
                .type(JsonValue.from("web_search_call"))
                .build());

        ResponseOutputItem fileSearchCall = ResponseOutputItem.ofFileSearchCall(ResponseFileSearchToolCall.builder()
                .id("file_1")
                .addQuery("langchain4j")
                .status(ResponseFileSearchToolCall.Status.COMPLETED)
                .type(JsonValue.from("file_search_call"))
                .build());

        ResponseOutputItem toolSearchCall = ResponseOutputItem.ofToolSearchCall(ResponseToolSearchCall.builder()
                .id("search_1")
                .arguments(JsonValue.from(Map.of("query", "weather")))
                .callId("tool_search_call_1")
                .execution(ResponseToolSearchCall.Execution.SERVER)
                .status(ResponseToolSearchCall.Status.COMPLETED)
                .createdBy("assistant")
                .type(JsonValue.from("tool_search_call"))
                .build());

        ResponseOutputItem mcpListTools = ResponseOutputItem.ofMcpListTools(ResponseOutputItem.McpListTools.builder()
                .id("mcp_list_1")
                .serverLabel("filesystem")
                .addTool(ResponseOutputItem.McpListTools.Tool.builder()
                        .inputSchema(JsonValue.from(Map.of("type", "object", "properties", Map.of())))
                        .name("read_file")
                        .build())
                .type(JsonValue.from("mcp_list_tools"))
                .build());

        ResponseOutputItem mcpCall = ResponseOutputItem.ofMcpCall(ResponseOutputItem.McpCall.builder()
                .id("mcp_1")
                .arguments("{\"path\":\"README.md\"}")
                .name("read_file")
                .serverLabel("filesystem")
                .status(ResponseOutputItem.McpCall.Status.COMPLETED)
                .type(JsonValue.from("mcp_call"))
                .build());

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(
                        List.of(webSearchCall, fileSearchCall, toolSearchCall, mcpListTools, mcpCall));

        assertThat(results)
                .extracting(OpenAiOfficialServerToolResult::type)
                .containsExactly(
                        "web_search_call", "file_search_call", "tool_search_call", "mcp_list_tools", "mcp_call");
    }
}
