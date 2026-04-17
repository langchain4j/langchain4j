package dev.langchain4j.model.openaiofficial.openai;

import com.openai.models.ChatModel;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesChatResponseMetadata;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialResponsesStreamingChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialServerTool;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialServerToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
                .maxOutputTokens(MAX_OUTPUT_TOKENS_MIN_VALUE).build());
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
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 0
                        && toolCall.id().equals(id1)
                        && toolCall.name().equals("getWeather")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 0
                            && request.id().equals(id1)
                            && request.name().equals("getWeather")
                            && request.arguments().replace(" ", "").equals("{\"city\":\"Munich\"}");
                }
        ));

        io.verify(handler, atLeast(1)).onPartialToolCall(argThat(toolCall ->
                toolCall.index() == 1
                        && toolCall.id().equals(id2)
                        && toolCall.name().equals("getTime")
                        && !toolCall.partialArguments().isBlank()
        ), any());
        io.verify(handler).onCompleteToolCall(argThat(toolCall ->
                {
                    ToolExecutionRequest request = toolCall.toolExecutionRequest();
                    return toolCall.index() == 1
                            && request.id().equals(id2)
                            && request.name().equals("getTime")
                            && request.arguments().replace(" ", "").equals("{\"country\":\"France\"}");
                }
        ));
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false;
    }

    @Test
    void should_support_web_search_server_tool() {
        StreamingChatModel model = OpenAiOfficialResponsesStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(GPT_5_4)
                .serverTools(OpenAiOfficialServerTool.builder()
                        .type("web_search")
                        .addAttribute("search_context_size", "low")
                        .addAttribute("filters", Map.of("allowed_domains", List.of("openai.com")))
                        .build())
                .returnServerToolResults(true)
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(
                        UserMessage.from(
                                "Use web search on openai.com to find the GPT-5.4 documentation page and answer with a one-sentence summary."))
                .build();

        AiMessage aiMessage = chat(model, chatRequest).chatResponse().aiMessage();

        assertThat(aiMessage.text()).isNotBlank();
        assertThat(serverToolResults(aiMessage))
                .extracting(OpenAiOfficialServerToolResult::type)
                .contains("web_search_call");
    }

    @SuppressWarnings("unchecked")
    private static List<OpenAiOfficialServerToolResult> serverToolResults(AiMessage aiMessage) {
        List<OpenAiOfficialServerToolResult> results = aiMessage.attribute("server_tool_results", List.class);
        assertThat(results).isNotNull().isNotEmpty();
        return results;
    }

    @Disabled("gpt-5.4-mini cannot do it properly")
    @Override
    protected void should_respect_JsonRawSchema_responseFormat(StreamingChatModel model) {
    }
}
