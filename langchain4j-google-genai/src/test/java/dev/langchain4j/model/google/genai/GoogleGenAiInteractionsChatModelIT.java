package dev.langchain4j.model.google.genai;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiInteractionsChatModelIT extends AbstractChatModelIT {

    static final GoogleGenAiInteractionsChatModel GOOGLE_GEN_AI_INTERACTIONS_CHAT_MODEL =
            GoogleGenAiInteractionsChatModel.builder()
                    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                    .modelName("gemini-3.8-flash")
                    .thinkingLevel("MINIMAL")
                    .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(GOOGLE_GEN_AI_INTERACTIONS_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleGenAiInteractionsChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gemini-3.8-flash"))
                .thinkingLevel("MINIMAL")
                .build();
    }

    @Override
    protected String customModelName() {
        return "gemini-3.1-flash-lite";
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
    protected boolean supportsChatAsync() {
        return false;
    }

    @Override
    protected boolean assertToolId(ChatModel model) {
        return true;
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return GoogleGenAiInteractionsChatResponseMetadata.class;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return true;
    }

    @Override
    protected void assertOutputTokenCount(TokenUsage tokenUsage, Integer maxOutputTokens) {
        assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens);
    }

    @Override
    protected void assertTotalTokenCount(TokenUsage tokenUsage) {
        assertThat(tokenUsage.totalTokenCount())
                .isGreaterThanOrEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel model) {
        return GoogleGenAiTokenUsage.class;
    }

    @Test
    void should_return_thinking_summaries_when_enabled() {
        GoogleGenAiInteractionsChatModel model = GoogleGenAiInteractionsChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3.8-flash")
                .thinkingLevel("LOW")
                .includeThoughts(true)
                .returnThinking(true)
                .timeout(ofMinutes(2))
                .build();

        ChatResponse response = model.chat(
                dev.langchain4j.data.message.UserMessage.from("How many r's are in strawberry? Think briefly."));

        assertThat(response.aiMessage().text()).contains("3");
        assertThat(response.aiMessage().thinking()).isNotBlank();
    }

    static class WeatherTools {

        @Tool("Get the current temperature in Celsius for a city")
        String getTemperature(String city) {
            return switch (city.toLowerCase()) {
                case "indore" -> "28C";
                case "pune" -> "26C";
                default -> "22C";
            };
        }
    }

    interface Assistant {

        String chat(String userMessage);
    }

    @Test
    void should_preserve_steps_across_serialized_chat_memory_with_parallel_tools() {
        GoogleGenAiInteractionsChatModel model = GoogleGenAiInteractionsChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3.8-flash")
                .thinkingLevel("LOW")
                .includeThoughts(true)
                .returnThinking(true)
                .temperature(0.0)
                .timeout(ofMinutes(2))
                .build();

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(memory)
                .tools(new WeatherTools())
                .build();

        String firstAnswer = assistant.chat(
                "Call getTemperature in parallel for both Indore and Pune, then summarize both temperatures.");
        assertThat(firstAnswer).contains("28").contains("26");

        // Simulate persisting ChatMemory to JSON and reloading it for a follow-up turn
        String serializedHistory = messagesToJson(memory.messages());
        List<ChatMessage> reloadedMessages = messagesFromJson(serializedHistory);
        assertThat(reloadedMessages.stream().filter(AiMessage.class::isInstance))
                .anySatisfy(msg -> assertThat(((AiMessage) msg).attributes()).containsKey("interaction_steps"));

        ChatMemory reloadedMemory = MessageWindowChatMemory.withMaxMessages(20);
        reloadedMessages.forEach(reloadedMemory::add);

        Assistant resumedAssistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(reloadedMemory)
                .tools(new WeatherTools())
                .build();

        String followUp = resumedAssistant.chat("Which of those two cities was warmer?");
        assertThat(followUp).containsIgnoringCase("Indore");
    }
}
