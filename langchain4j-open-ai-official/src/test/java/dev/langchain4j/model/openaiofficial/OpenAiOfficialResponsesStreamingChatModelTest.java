package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolSearchTool;
import com.openai.models.responses.WebSearchTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiOfficialResponsesStreamingChatModelTest {

    @Test
    void should_store_server_tools_in_streaming_default_request_parameters() {
        Tool webSearch = webSearchTool();

        OpenAiOfficialResponsesStreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .modelName("gpt-5.4-mini")
                .apiKey("banana")
                .serverTools(webSearch)
                .build();

        OpenAiOfficialResponsesChatRequestParameters parameters =
                (OpenAiOfficialResponsesChatRequestParameters) model.defaultRequestParameters();

        assertThat(parameters.serverTools()).containsExactly(webSearch);
    }

    @Test
    void should_store_server_tools_in_chat_default_request_parameters() {
        Tool toolSearch = toolSearchTool();

        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .modelName("gpt-5.4-mini")
                .apiKey("banana")
                .serverTools(toolSearch)
                .build();

        OpenAiOfficialResponsesChatRequestParameters parameters =
                (OpenAiOfficialResponsesChatRequestParameters) model.defaultRequestParameters();

        assertThat(parameters.serverTools()).containsExactly(toolSearch);
    }

    @Test
    void should_merge_server_tools_in_request_parameters() {
        Tool webSearch = webSearchTool();
        Tool toolSearch = toolSearchTool();

        OpenAiOfficialResponsesChatRequestParameters defaults =
                OpenAiOfficialResponsesChatRequestParameters.builder()
                        .modelName("gpt-5.4-mini")
                        .serverTools(List.of(webSearch))
                        .build();

        OpenAiOfficialResponsesChatRequestParameters override =
                OpenAiOfficialResponsesChatRequestParameters.builder()
                        .serverTools(List.of(toolSearch))
                        .build();

        OpenAiOfficialResponsesChatRequestParameters merged = defaults.overrideWith(override);

        assertThat(merged.serverTools()).containsExactly(toolSearch);
    }

    @Test
    void should_include_function_and_server_tools_in_request_params() {
        Tool webSearch = webSearchTool();
        ToolSpecification functionTool = ToolSpecification.builder()
                .name("getWeather")
                .description("Returns the current weather for a given city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        OpenAiOfficialResponsesChatRequestParameters parameters =
                OpenAiOfficialResponsesChatRequestParameters.builder()
                        .modelName("gpt-5.4-mini")
                        .toolSpecifications(List.of(functionTool))
                        .toolChoice(ToolChoice.REQUIRED)
                        .serverTools(List.of(webSearch))
                        .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(parameters)
                .build();

        var requestParams = OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(chatRequest, parameters);

        assertThat(requestParams.tools()).hasValueSatisfying(tools -> {
            assertThat(tools).hasSize(2);
            assertThat(tools.get(0).isFunction()).isTrue();
            assertThat(tools.get(1)).isEqualTo(webSearch);
        });
    }

    @Test
    void should_store_server_tool_results_in_response_metadata() {
        List<OpenAiOfficialServerToolResult> results = List.of(OpenAiOfficialServerToolResult.builder()
                .type("web_search_call")
                .toolUseId("call_1")
                .content(Map.of("type", "web_search_call"))
                .build());

        OpenAiOfficialResponsesChatResponseMetadata metadata = OpenAiOfficialResponsesChatResponseMetadata.builder()
                .id("resp_123")
                .modelName("gpt-5.4")
                .serverToolResults(results)
                .build();

        assertThat(metadata.serverToolResults()).containsExactlyElementsOf(results);
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
        assertThat(results)
                .extracting(OpenAiOfficialServerToolResult::toolUseId)
                .containsExactly("shell_call_1", "computer_call_1");
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
        assertThat(results.get(0).toolUseId()).isEqualTo("computer_call_2");
        assertThat(contentOf(results.get(0)))
                .containsEntry("id", "computer_2")
                .containsEntry("call_id", "computer_call_2")
                .containsEntry("type", "computer_call")
                .containsKey("action")
                .doesNotContainKey("pending_safety_checks");
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
        assertThat(results.get(0).toolUseId()).isEqualTo("shell_call_1");
        assertThat(contentOf(results.get(0)))
                .containsEntry("id", "shell_out_1")
                .containsEntry("call_id", "shell_call_1")
                .containsEntry("type", "shell_call_output")
                .containsKey("max_output_length")
                .containsKey("output");
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

        ResponseOutputItem mcpApprovalRequest =
                ResponseOutputItem.ofMcpApprovalRequest(ResponseOutputItem.McpApprovalRequest.builder()
                        .id("mcp_approval_1")
                        .arguments("{\"path\":\"README.md\"}")
                        .name("read_file")
                        .serverLabel("filesystem")
                        .type(JsonValue.from("mcp_approval_request"))
                        .build());

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(List.of(
                        webSearchCall, fileSearchCall, toolSearchCall, mcpListTools, mcpApprovalRequest, mcpCall));

        assertThat(results)
                .extracting(OpenAiOfficialServerToolResult::type)
                .containsExactly(
                        "web_search_call",
                        "file_search_call",
                        "tool_search_call",
                        "mcp_list_tools",
                        "mcp_approval_request",
                        "mcp_call");
    }

    @Test
    void should_extract_raw_json_for_documented_output_items_not_modeled_as_typed_arms() {
        ResponseOutputItem computerCallOutput = responseOutputItem("""
                {
                  "id": "computer_out_1",
                  "call_id": "computer_call_1",
                  "type": "computer_call_output",
                  "status": "completed",
                  "output": {
                    "type": "computer_screenshot",
                    "image_url": "data:image/png;base64,abc"
                  }
                }
                """);

        ResponseOutputItem localShellCallOutput = responseOutputItem("""
                {
                  "id": "local_shell_out_1",
                  "call_id": "local_shell_call_1",
                  "type": "local_shell_call_output",
                  "status": "completed",
                  "output": [
                    {
                      "type": "logs",
                      "logs": "hello"
                    }
                  ]
                }
                """);

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(
                        List.of(computerCallOutput, localShellCallOutput));

        assertThat(results)
                .extracting(OpenAiOfficialServerToolResult::type)
                .containsExactly("computer_call_output", "local_shell_call_output");
        assertThat(results)
                .extracting(OpenAiOfficialServerToolResult::toolUseId)
                .containsExactly("computer_call_1", "local_shell_call_1");
        assertThat(contentOf(results.get(0)))
                .containsEntry("id", "computer_out_1")
                .containsEntry("call_id", "computer_call_1")
                .containsEntry("type", "computer_call_output");
        assertThat(contentOf(results.get(1)))
                .containsEntry("id", "local_shell_out_1")
                .containsEntry("call_id", "local_shell_call_1")
                .containsEntry("type", "local_shell_call_output");
    }

    @Test
    void should_extract_additional_built_in_tool_items_using_raw_json_content() {
        ResponseOutputItem toolSearchOutput = responseOutputItem("""
                {
                  "id": "tool_search_output_1",
                  "call_id": "tool_search_call_1",
                  "execution": "server",
                  "status": "completed",
                  "tools": [],
                  "type": "tool_search_output"
                }
                """);

        ResponseOutputItem applyPatchCall = responseOutputItem("""
                {
                  "id": "patch_call_1",
                  "call_id": "patch_request_1",
                  "operation": {
                    "type": "create_file",
                    "path": "README.md",
                    "content": "hello"
                  },
                  "status": "completed",
                  "type": "apply_patch_call"
                }
                """);

        List<OpenAiOfficialServerToolResult> results =
                OpenAiOfficialResponsesStreamingChatModel.extractServerToolResults(List.of(toolSearchOutput, applyPatchCall));

        assertThat(results)
                .extracting(OpenAiOfficialServerToolResult::type)
                .containsExactly("tool_search_output", "apply_patch_call");
    }

    private static Tool webSearchTool() {
        return Tool.ofWebSearch(WebSearchTool.builder()
                .type(WebSearchTool.Type.of("web_search"))
                .filters(WebSearchTool.Filters.builder()
                        .allowedDomains(List.of("developers.openai.com"))
                        .build())
                .build());
    }

    private static Tool toolSearchTool() {
        return Tool.ofSearch(ToolSearchTool.builder()
                .type(JsonValue.from("tool_search"))
                .description("Search tools")
                .build());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contentOf(OpenAiOfficialServerToolResult result) {
        return (Map<String, Object>) result.content();
    }

    private static ResponseOutputItem responseOutputItem(String json) {
        try {
            return ObjectMappers.jsonMapper().readValue(json, ResponseOutputItem.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
