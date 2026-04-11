package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.core.ObjectMappers;
import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseComputerToolCall;
import com.openai.models.responses.ResponseFileSearchToolCall;
import com.openai.models.responses.ResponseFunctionShellToolCall;
import com.openai.models.responses.ResponseFunctionShellToolCallOutput;
import com.openai.models.responses.ResponseFunctionWebSearch;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseToolSearchCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiOfficialResponsesStreamingChatModelTest {

    @Test
    void should_convert_shell_server_tool() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "local",
                        "skills", List.of(Map.of(
                                "name", "checks",
                                "path", "/tmp/checks",
                                "description", "Run checks"))))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isShell()).isTrue();
        assertThat(tool.asShell().environment()).isPresent();
        assertThat(tool.asShell().environment().get().isLocal()).isTrue();
        assertThat(tool.asShell().environment().get().asLocal().skills().get()).hasSize(1);
    }

    @Test
    void should_convert_computer_server_tool() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("computer")
                .addAttribute("display_width", 1024)
                .addAttribute("display_height", 768)
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isComputer()).isTrue();
        assertThat(tool.asComputer()._additionalProperties()).containsKeys("display_width", "display_height");
    }

    @Test
    void should_convert_namespace_server_tool() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("namespace")
                .name("github")
                .addAttribute("description", "GitHub tools")
                .addAttribute("tools", List.of(Map.of(
                        "type", "function",
                        "name", "list_prs",
                        "description", "List pull requests",
                        "parameters", Map.of("type", "object", "properties", Map.of()),
                        "strict", true)))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isNamespace()).isTrue();
        assertThat(tool.asNamespace().name()).isEqualTo("github");
        assertThat(tool.asNamespace().tools()).hasSize(1);
        assertThat(tool.asNamespace().tools().get(0).isFunction()).isTrue();
    }

    @Test
    void should_preserve_namespace_function_additional_properties() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("namespace")
                .name("crm")
                .addAttribute("description", "CRM tools")
                .addAttribute("tools", List.of(Map.of(
                        "type", "function",
                        "name", "list_open_orders",
                        "description", "List open orders for a customer ID.",
                        "defer_loading", true,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of("customer_id", Map.of("type", "string")),
                                "required", List.of("customer_id")))))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

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
                .addAttribute("tools", List.of(Map.of(
                        "type", "custom",
                        "name", "search_code",
                        "description", "Search code",
                        "defer_loading", true)))
                .build();

        assertThatThrownBy(() -> invokeToResponsesServerTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported namespace nested tool type: custom");
    }

    @Test
    void should_merge_function_and_server_tools() {
        ToolSpecification functionTool = ToolSpecification.builder()
                .name("weather")
                .description("Get weather")
                .build();

        OpenAiOfficialServerTool shellTool = OpenAiOfficialServerTool.builder().type("shell").build();

        var tools = invokeToResponsesTools(
                List.of(functionTool), false, List.of(shellTool));

        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).isFunction()).isTrue();
        assertThat(tools.get(1).isShell()).isTrue();
    }

    @Test
    void should_convert_web_search_server_tool_with_attributes() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("web_search")
                .addAttribute("search_context_size", "low")
                .addAttribute("filters", Map.of("allowed_domains", List.of("openai.com")))
                .addAttribute("latency_tier", "interactive")
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isWebSearch()).isTrue();
        assertThat(tool.asWebSearch().searchContextSize().get().asString()).isEqualTo("low");
        assertThat(tool.asWebSearch()._additionalProperties()).containsKey("latency_tier");
    }

    @Test
    void should_convert_legacy_web_search_domain_attributes_into_filters_only() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("web_search")
                .addAttribute("allowed_domains", List.of("openai.com"))
                .addAttribute("blocked_domains", List.of("example.com"))
                .addAttribute("latency_tier", "interactive")
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isWebSearch()).isTrue();
        assertThat(tool.asWebSearch().filters()).isPresent();
        assertThat(tool.asWebSearch().filters().get().allowedDomains())
                .hasValue(List.of("openai.com"));
        assertThat(tool.asWebSearch().filters().get()._additionalProperties())
                .containsEntry("blocked_domains", JsonValue.from(List.of("example.com")));
        assertThat(tool.asWebSearch()._additionalProperties())
                .containsKey("latency_tier")
                .doesNotContainKeys("allowed_domains", "blocked_domains");
    }

    @Test
    void should_keep_backward_compatible_attributes_api() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("tool_search")
                .addAttribute("description", "Search tools")
                .addAttribute("execution", "required")
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isSearch()).isTrue();
        assertThat(tool.asSearch().description().get()).isEqualTo("Search tools");
        assertThat(tool.asSearch().execution().get().asString()).isEqualTo("required");
    }

    @Test
    void should_convert_file_search_server_tool_with_attributes() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("file_search")
                .addAttribute("vector_store_ids", List.of("vs_1", "vs_2"))
                .addAttribute("max_num_results", 5)
                .addAttribute("filters", Map.of("type", "eq", "key", "source", "value", "docs"))
                .addAttribute("ranking_options", Map.of("ranker", "auto"))
                .addAttribute("rewrite_query", true)
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isFileSearch()).isTrue();
        assertThat(tool.asFileSearch().vectorStoreIds()).containsExactly("vs_1", "vs_2");
        assertThat(tool.asFileSearch().maxNumResults()).hasValue(5L);
        assertThat(tool.asFileSearch().rankingOptions()).isPresent();
        assertThat(tool.asFileSearch()._additionalProperties()).containsKey("rewrite_query");
    }

    @Test
    void should_convert_mcp_server_tool_with_attributes() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("mcp")
                .name("filesystem")
                .addAttribute("server_label", "filesystem")
                .addAttribute("server_url", "https://example.com/mcp")
                .addAttribute("allowed_tools", List.of("read_file"))
                .addAttribute("headers", Map.of("x-test", "1"))
                .addAttribute("require_approval", "never")
                .addAttribute("trace", true)
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isMcp()).isTrue();
        assertThat(tool.asMcp().serverLabel()).isEqualTo("filesystem");
        assertThat(tool.asMcp().serverUrl()).hasValue("https://example.com/mcp");
        assertThat(tool.asMcp().allowedTools()).isPresent();
        assertThat(tool.asMcp().headers()).isPresent();
        assertThat(tool.asMcp().requireApproval()).isPresent();
        assertThat(tool.asMcp()._additionalProperties()).containsKey("trace");
    }

    @Test
    void should_convert_mcp_allowed_tools_filter_object() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("mcp")
                .name("filesystem")
                .addAttribute("server_label", "filesystem")
                .addAttribute("server_url", "https://example.com/mcp")
                .addAttribute("allowed_tools", Map.of(
                        "read_only", true,
                        "tool_names", List.of("read_file")))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);
        var allowedTools = tool.asMcp().allowedTools().get().validate();

        assertThat(tool.isMcp()).isTrue();
        assertThat(tool.asMcp().allowedTools()).isPresent();
        assertThat(allowedTools.isMcpToolFilter()).isTrue();
        assertThat(allowedTools.asMcpToolFilter().readOnly()).hasValue(true);
        assertThat(allowedTools.asMcpToolFilter().toolNames())
                .hasValue(List.of("read_file"));
    }

    @Test
    void should_convert_mcp_require_approval_filter_object() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("mcp")
                .name("filesystem")
                .addAttribute("server_label", "filesystem")
                .addAttribute("server_url", "https://example.com/mcp")
                .addAttribute("require_approval", Map.of(
                        "always", Map.of("tool_names", List.of("write_file")),
                        "never", Map.of("read_only", true)))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);
        var requireApproval = tool.asMcp().requireApproval().get().validate();

        assertThat(tool.isMcp()).isTrue();
        assertThat(tool.asMcp().requireApproval()).isPresent();
        assertThat(requireApproval.isMcpToolApprovalFilter()).isTrue();
        assertThat(requireApproval.asMcpToolApprovalFilter().always())
                .isPresent()
                .get()
                .extracting(value -> value.toolNames().orElse(List.of()))
                .isEqualTo(List.of("write_file"));
        assertThat(requireApproval.asMcpToolApprovalFilter().never())
                .isPresent()
                .get()
                .extracting(value -> value.readOnly().orElse(false))
                .isEqualTo(true);
    }

    @Test
    void should_convert_shell_container_allowlist_domain_secrets() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "container_auto",
                        "network_policy", Map.of(
                                "type", "allowlist",
                                "allowed_domains", List.of("httpbin.org"),
                                "domain_secrets", List.of(Map.of(
                                        "domain", "httpbin.org",
                                        "name", "API_KEY",
                                        "value", "debug-secret-123")))))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isShell()).isTrue();
        assertThat(tool.asShell().environment()).isPresent();
        assertThat(tool.asShell().environment().get().isContainerAuto()).isTrue();
        assertThat(tool.asShell().environment().get().asContainerAuto().networkPolicy()).isPresent();
        assertThat(tool.asShell().environment().get().asContainerAuto().networkPolicy().get().isAllowlist()).isTrue();
        assertThat(tool.asShell().environment().get().asContainerAuto().networkPolicy().get().asAllowlist().domainSecrets())
                .hasValueSatisfying(domainSecrets -> assertThat(domainSecrets).hasSize(1));
    }

    @Test
    void should_convert_shell_container_reference_environment() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "container_reference",
                        "container_id", "container_123"))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isShell()).isTrue();
        assertThat(tool.asShell().environment()).isPresent();
        assertThat(tool.asShell().environment().get().isContainerReference()).isTrue();
        assertThat(tool.asShell().environment().get().asContainerReference().containerId()).isEqualTo("container_123");
    }

    @Test
    void should_convert_shell_container_reference_skill() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "container_auto",
                        "skills", List.of(Map.of(
                                "type", "reference",
                                "skill_id", "checks",
                                "version", "1"))))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isShell()).isTrue();
        assertThat(tool.asShell().environment()).isPresent();
        assertThat(tool.asShell().environment().get().isContainerAuto()).isTrue();
        assertThat(tool.asShell().environment().get().asContainerAuto().skills())
                .hasValueSatisfying(skills -> {
                    assertThat(skills).hasSize(1);
                    assertThat(skills.get(0).isReference()).isTrue();
                    assertThat(skills.get(0).asReference().skillId()).isEqualTo("checks");
                    assertThat(skills.get(0).asReference().version()).hasValue("1");
                });
    }

    @Test
    void should_convert_shell_container_inline_skill() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "container_auto",
                        "skills", List.of(Map.of(
                                "type", "inline",
                                "name", "checks",
                                "description", "Run checks",
                                "source", Map.of(
                                        "type", "source",
                                        "media_type", "text/markdown",
                                        "data", "# checks")))))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isShell()).isTrue();
        assertThat(tool.asShell().environment()).isPresent();
        assertThat(tool.asShell().environment().get().isContainerAuto()).isTrue();
        assertThat(tool.asShell().environment().get().asContainerAuto().skills())
                .hasValueSatisfying(skills -> {
                    assertThat(skills).hasSize(1);
                    assertThat(skills.get(0).isInline()).isTrue();
                    assertThat(skills.get(0).asInline().name()).isEqualTo("checks");
                    assertThat(skills.get(0).asInline().source()).isNotNull();
                });
    }

    @Test
    void should_convert_shell_container_disabled_network_policy() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "container_auto",
                        "network_policy", Map.of("type", "disabled")))
                .build();

        var tool = invokeToResponsesServerTool(serverTool);

        assertThat(tool.isShell()).isTrue();
        assertThat(tool.asShell().environment()).isPresent();
        assertThat(tool.asShell().environment().get().isContainerAuto()).isTrue();
        assertThat(tool.asShell().environment().get().asContainerAuto().networkPolicy())
                .hasValueSatisfying(networkPolicy -> assertThat(networkPolicy.isDisabled()).isTrue());
    }

    @Test
    void should_reject_unsupported_shell_environment_type() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of("type", "remote"))
                .build();

        assertThatThrownBy(() -> invokeToResponsesServerTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported shell environment type: remote");
    }

    @Test
    void should_reject_unsupported_shell_container_skill_type() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "container_auto",
                        "skills", List.of(Map.of("type", "custom"))))
                .build();

        assertThatThrownBy(() -> invokeToResponsesServerTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported shell container skill type: custom");
    }

    @Test
    void should_reject_unsupported_shell_container_network_policy_type() {
        OpenAiOfficialServerTool serverTool = OpenAiOfficialServerTool.builder()
                .type("shell")
                .addAttribute("environment", Map.of(
                        "type", "container_auto",
                        "network_policy", Map.of("type", "restricted")))
                .build();

        assertThatThrownBy(() -> invokeToResponsesServerTool(serverTool))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported shell container network_policy type: restricted");
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

        assertThat(results).hasSize(2);
        assertThat(results).extracting(OpenAiOfficialServerToolResult::type)
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
        ResponseOutputItem shellCallOutput = ResponseOutputItem.ofShellCallOutput(ResponseFunctionShellToolCallOutput.builder()
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
    void should_extract_local_shell_server_tool_results() {
        ResponseOutputItem localShellCall = ResponseOutputItem.ofLocalShellCall(
                ResponseOutputItem.LocalShellCall.builder()
                        .id("local_shell_1")
                        .callId("local_shell_call_1")
                        .action(ResponseOutputItem.LocalShellCall.Action.builder()
                                .addCommand("pwd")
                                .env(ResponseOutputItem.LocalShellCall.Action.Env.builder().build())
                                .type(JsonValue.from("exec"))
                                .build())
                        .status(ResponseOutputItem.LocalShellCall.Status.COMPLETED)
                        .type(JsonValue.from("shell_call"))
                        .build());

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(List.of(localShellCall));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo("shell_call");
        assertThat(results.get(0).toolUseId()).isEqualTo("local_shell_1");
        assertThat(((Map<String, Object>) results.get(0).content()).keySet()).contains("id", "call_id", "action", "status");
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
                        .inputSchema(JsonValue.from(Map.of(
                                "type", "object",
                                "properties", Map.of())))
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

        List<OpenAiOfficialServerToolResult> results = OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(
                List.of(webSearchCall, fileSearchCall, toolSearchCall, mcpListTools, mcpCall));

        assertThat(results).extracting(OpenAiOfficialServerToolResult::type)
                .containsExactly("web_search_call", "file_search_call", "tool_search_call", "mcp_list_tools", "mcp_call");
        assertThat(((Map<String, Object>) results.get(0).content()).keySet()).contains("id", "action", "status");
        assertThat(((Map<String, Object>) results.get(1).content()).keySet()).contains("id", "queries", "status");
        assertThat(((Map<String, Object>) results.get(2).content()).keySet()).contains("id", "arguments", "execution", "status");
        assertThat(((Map<String, Object>) results.get(3).content()).keySet()).contains("id", "server_label", "tools");
        assertThat(((Map<String, Object>) results.get(4).content()).keySet()).contains("id", "arguments", "name", "server_label");
    }

    @Test
    void should_build_final_ai_message_with_server_tool_results_attribute() {
        OpenAiOfficialServerToolResult serverToolResult = OpenAiOfficialServerToolResult.builder()
                .type("shell_call")
                .toolUseId("shell_1")
                .content(Map.of("status", "completed"))
                .build();

        AiMessage aiMessage = OpenAiOfficialResponsesStreamingChatModel.buildFinalAiMessage(
                "",
                List.of(),
                List.of(serverToolResult));

        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.attributes()).containsKey(OpenAiOfficialResponsesStreamingChatModel.SERVER_TOOL_RESULTS_KEY);
    }

    @Test
    void should_keep_null_text_for_tool_only_ai_message() {
        AiMessage aiMessage = OpenAiOfficialResponsesStreamingChatModel.buildFinalAiMessage(
                "",
                List.of(ToolExecutionRequest.builder()
                        .id("call_1")
                        .name("weather")
                        .arguments("{}")
                        .build()),
                List.of());

        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
    }

    @Test
    void should_reject_web_search_preview_server_tool() {
        OpenAiOfficialServerTool serverTool =
                OpenAiOfficialServerTool.builder().type("web_search_preview").build();

        assertThatThrownBy(() -> invokeToResponsesServerTool(serverTool))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("Supported types are: web_search, file_search, tool_search, mcp, shell, computer, namespace.");
    }

    private static com.openai.models.responses.Tool invokeToResponsesServerTool(OpenAiOfficialServerTool serverTool) {
        return OpenAiOfficialServerToolMapper.toResponsesTool(serverTool);
    }

    @SuppressWarnings("unchecked")
    private static List<com.openai.models.responses.Tool> invokeToResponsesTools(
            List<ToolSpecification> toolSpecifications, boolean strict, List<OpenAiOfficialServerTool> serverTools) {
        try {
            Method method = OpenAiOfficialResponsesStreamingChatModel.class
                    .getDeclaredMethod("toResponsesTools", List.class, boolean.class, List.class);
            method.setAccessible(true);
            return (List<com.openai.models.responses.Tool>) method.invoke(null, toolSpecifications, strict, serverTools);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
