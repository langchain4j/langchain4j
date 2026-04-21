package dev.langchain4j.model.openaiofficial.openai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;

import com.openai.core.ObjectMappers;
import com.openai.models.ChatModel;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialServerTool;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialServerToolResult;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialResponsesStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private static final String GPT_5_4 = "gpt-5.4";
    private static final String GPT_5_4_MINI = "gpt-5.4-mini";
    private static final int MAX_OUTPUT_TOKENS_MIN_VALUE = 16;

    @Override
    protected List<StreamingChatModel> models() {
        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4_MINI)
                .build();

        return List.of(model);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gpt-5.4-mini"))
                .build();
    }

    @Override
    protected String customModelName() {
        return ChatModel.GPT_4O_2024_11_20.toString();
    }

    @Override
    protected int maxOutputTokens() {
        return MAX_OUTPUT_TOKENS_MIN_VALUE;
    }

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters.overrideWith(ChatRequestParameters.builder()
                .maxOutputTokens(MAX_OUTPUT_TOKENS_MIN_VALUE)
                .build());
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return OpenAiOfficialChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
        return OpenAiOfficialResponsesChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel model) {
        return OpenAiOfficialTokenUsage.class;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-5.4-mini")
                .listeners(listener)
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler, atLeast(1))
                .onPartialToolCall(
                        argThat(toolCall -> toolCall.index() == 0
                                && toolCall.id().equals(id)
                                && toolCall.name().equals("getWeather")
                                && !toolCall.partialArguments().isBlank()),
                        any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id)
                    && request.name().equals("getWeather")
                    && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
        }));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler, atLeast(1))
                .onPartialToolCall(
                        argThat(toolCall -> toolCall.index() == 0
                                && toolCall.id().equals(id1)
                                && toolCall.name().equals("getWeather")
                                && !toolCall.partialArguments().isBlank()),
                        any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 0
                    && request.id().equals(id1)
                    && request.name().equals("getWeather")
                    && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
        }));

        io.verify(handler, atLeast(1))
                .onPartialToolCall(
                        argThat(toolCall -> toolCall.index() == 1
                                && toolCall.id().equals(id2)
                                && toolCall.name().equals("getTime")
                                && !toolCall.partialArguments().isBlank()),
                        any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall -> {
            ToolExecutionRequest request = toolCall.toolExecutionRequest();
            return toolCall.index() == 1
                    && request.id().equals(id2)
                    && request.name().equals("getTime")
                    && request.arguments().replace(" ", "").equals("{\"country\":\"France\"}");
        }));
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false;
    }

    @Test
    void should_execute_real_web_search_server_tool_request() {
        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4)
                .serverTools(OpenAiOfficialServerTool.builder()
                        .type("web_search")
                        .addAttribute("allowed_domains", List.of("developers.openai.com"))
                        .build())
                .returnServerToolResults(true)
                .defaultRequestParameters(OpenAiOfficialResponsesChatRequestParameters.builder()
                        .toolChoice(ToolChoice.REQUIRED)
                        .build())
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(
                "Use web search on the OpenAI developer docs and answer with one short sentence about the web search tool.",
                handler);

        var response = handler.get();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.aiMessage().attributes()).containsKey("server_tool_results");

        List<OpenAiOfficialServerToolResult> serverToolResults =
                response.aiMessage().attribute("server_tool_results", List.class);
        assertThat(serverToolResults).isNotEmpty();
        assertThat(serverToolResults)
                .extracting(OpenAiOfficialServerToolResult::type)
                .contains("web_search_call");
    }

    @Test
    void should_execute_real_tool_search_with_namespace_and_deferred_function_loading() {
        OpenAiOfficialServerTool toolSearch =
                OpenAiOfficialServerTool.builder().type("tool_search").build();

        OpenAiOfficialServerTool namespace = OpenAiOfficialServerTool.builder()
                .type("namespace")
                .name("crm")
                .addAttribute("description", "CRM tools")
                .addAttribute(
                        "tools",
                        List.of(
                                Map.of(
                                        "type",
                                        "function",
                                        "name",
                                        "get_customer_profile",
                                        "description",
                                        "Fetch a customer profile by customer ID.",
                                        "parameters",
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.of("customer_id", Map.of("type", "string")),
                                                "required",
                                                List.of("customer_id"),
                                                "additionalProperties",
                                                false)),
                                Map.of(
                                        "type",
                                        "function",
                                        "name",
                                        "list_open_orders",
                                        "description",
                                        "List open orders for a customer ID.",
                                        "defer_loading",
                                        true,
                                        "parameters",
                                        Map.of(
                                                "type",
                                                "object",
                                                "properties",
                                                Map.of("customer_id", Map.of("type", "string")),
                                                "required",
                                                List.of("customer_id"),
                                                "additionalProperties",
                                                false))))
                .build();

        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4)
                .serverTools(toolSearch, namespace)
                .returnServerToolResults(true)
                .defaultRequestParameters(OpenAiOfficialResponsesChatRequestParameters.builder()
                        .parallelToolCalls(false)
                        .build())
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("List open orders for customer CUST-12345.", handler);

        var response = handler.get();
        assertThat(response.aiMessage().attributes()).containsKey("server_tool_results");

        List<OpenAiOfficialServerToolResult> serverToolResults =
                response.aiMessage().attribute("server_tool_results", List.class);
        assertThat(serverToolResults)
                .extracting(OpenAiOfficialServerToolResult::type)
                .contains("tool_search_call", "tool_search_output");

        Map<String, Object> toolSearchOutput = serverToolResults.stream()
                .filter(result -> "tool_search_output".equals(result.type()))
                .findFirst()
                .map(result -> (Map<String, Object>) result.content())
                .orElseThrow();

        assertThat(toolSearchOutput).containsEntry("type", "tool_search_output");
        assertThat(toolSearchOutput.get("tools")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dynamicallyLoadedTools = ((List<?>) toolSearchOutput.get("tools")).stream()
                .map(item -> {
                    assertThat(item).isInstanceOf(Map.class);
                    return (Map<String, Object>) item;
                })
                .toList();
        assertThat(dynamicallyLoadedTools)
                .extracting(tool -> String.valueOf(tool.get("name")))
                .contains("crm");

        ToolExecutionRequest listOpenOrdersRequest = response.aiMessage().toolExecutionRequests().stream()
                .filter(request -> "list_open_orders".equals(request.name()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> arguments;
        try {
            arguments = ObjectMappers.jsonMapper().readValue(listOpenOrdersRequest.arguments(), LinkedHashMap.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(arguments).containsEntry("customer_id", "CUST-12345");
    }

    @Disabled("gpt-5.4-mini cannot do it properly")
    @Override
    protected void should_respect_JsonRawSchema_responseFormat(StreamingChatModel model) {}
}
