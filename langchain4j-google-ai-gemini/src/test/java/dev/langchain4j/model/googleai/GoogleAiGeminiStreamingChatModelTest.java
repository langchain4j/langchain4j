package dev.langchain4j.model.googleai;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.List;
import java.util.Random;
import me.kpavlov.aimocks.gemini.MockGemini;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiStreamingChatModelTest {
    private static final ChatRequest DEFAULT_REQUEST =
            ChatRequest.builder().messages(new UserMessage("Hi")).build();

    private static final MockGemini MOCK = new MockGemini();
    public static final Random RANDOM = new Random();

    private int seed = -1;
    private double temperature = -1;
    private String modelName = "gemini-2.0-flash";

    @BeforeEach
    void beforeEach() {
        seed = RANDOM.nextInt(1, 100500);
        temperature = RANDOM.nextInt(100, 1000) / 1000d;
        modelName = "gemini";
    }

    @Test
    void test_streaming_request() {
        final var chatModel = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey("ApiKey")
                .modelName(modelName)
                .baseUrl(MOCK.baseUrl())
                .seed(seed)
                .build();
        final var systemMessage = "You are a AI agent. Always respond professionally";
        final var userMessage = "Hey, please respond with Hello world!";

        MOCK.generateContentStream(req -> {
                    req.path("/models/%s:streamGenerateContent".formatted(modelName));
                    req.seed(seed);
                    req.systemMessageContains(systemMessage);
                    req.userMessageContains(userMessage);
                    req.model(modelName);
                    req.temperature(temperature);
                })
                .respondsStream(res -> {
                    res.setResponseChunks(List.of("Hello", " world!"));
                });

        final var responseHandler = new TestStreamingChatResponseHandler();

        final var request = ChatRequest.builder()
                .messages(systemMessage(systemMessage), userMessage(userMessage))
                .parameters(DefaultChatRequestParameters.builder()
                        .temperature(temperature)
                        .topP(0.85)
                        .build())
                .build();

        chatModel.chat(request, responseHandler);

        final var chatResponse = responseHandler.get();

        assertThat(chatResponse.aiMessage().text()).isEqualTo("Hello world!");
    }

    @Nested
    class GoogleAiGeminiStreamingChatModelBuilder {

        @Test
        void seedParameterInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .seed(42)
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThat(Json.toJson(result.getGenerationConfig())).contains("\"seed\" : 42");
        }

        @Test
        void defaultSeedInContentRequest() {
            GoogleAiGeminiStreamingChatModel chatModel = GoogleAiGeminiStreamingChatModel.builder()
                    .apiKey("ApiKey")
                    .modelName("ModelName")
                    .build();
            GeminiGenerateContentRequest result = chatModel.createGenerateContentRequest(DEFAULT_REQUEST);

            assertThat(Json.toJson(result.getGenerationConfig())).doesNotContain("\"seed\"");
        }
    }
}
