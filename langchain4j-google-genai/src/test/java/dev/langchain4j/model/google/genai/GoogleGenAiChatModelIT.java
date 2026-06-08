package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiChatModelIT extends AbstractChatModelIT {

    static final GoogleGenAiChatModel GOOGLE_GEN_AI_CHAT_MODEL = GoogleGenAiChatModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName("gemini-2.5-flash")
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(GOOGLE_GEN_AI_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gemini-2.5-flash"))
                .build();
    }

    @Override
    protected String customModelName() {
        return "gemini-2.5-pro";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false; // TCK uses raw schema string which Gemini SDK doesn't natively map in this builder logic
    }

    @Override
    protected boolean assertToolId(ChatModel model) {
        return false; // TODO
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return GoogleGenAiChatResponseMetadata.class;
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO
    }

    @Override
    protected void assertOutputTokenCount(TokenUsage tokenUsage, Integer maxOutputTokens) {
        assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens); // TODO
    }

    @Test
    void should_persist_thought_signature_in_multi_turn_tool_execution() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3.1-pro-preview")
                .temperature(0.0)
                .build();

        ToolSpecification tool = ToolSpecification.builder()
                .name("get_weather")
                .description("Get the weather in a city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city", "the city")
                        .build())
                .build();

        UserMessage userMsg = UserMessage.from("What is the weather in London?");

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(userMsg))
                .toolSpecifications(List.of(tool))
                .toolChoice(ToolChoice.REQUIRED)
                .build();

        ChatResponse response1 = model.chat(request);
        AiMessage aiMsg = response1.aiMessage();

        assertThat(aiMsg.hasToolExecutionRequests()).isTrue();
        ToolExecutionRequest toolRequest = aiMsg.toolExecutionRequests().get(0);
        assertThat(toolRequest.id()).isNotNull();

        ToolExecutionResultMessage toolMsg =
                ToolExecutionResultMessage.from(toolRequest, "The weather is 20 degrees and sunny.");

        ChatRequest request2 = ChatRequest.builder()
                .messages(List.of(userMsg, aiMsg, toolMsg))
                .toolSpecifications(List.of(tool))
                .build();

        ChatResponse response2 = model.chat(request2);

        // This confirms the second request succeeded without an INVALID_ARGUMENT error
        assertThat(response2.aiMessage().text()).isNotBlank();
        assertThat(response2.aiMessage().hasToolExecutionRequests()).isFalse();
    }
}
