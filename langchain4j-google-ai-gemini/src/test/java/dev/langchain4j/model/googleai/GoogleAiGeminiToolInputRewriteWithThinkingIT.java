package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SpyingHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A tool input guardrail can rewrite the arguments of a tool call, and the rewritten call replaces the original one in
 * the chat memory. Gemini returns a thought signature with a function call, and LangChain4j sends it back with that
 * call. This checks that Gemini accepts the signed call whose arguments a guardrail rewrote.
 */
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiToolInputRewriteWithThinkingIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String REWRITTEN_CITY = "Munich, Germany";

    interface Assistant {

        String chat(String userMessage);
    }

    static class WeatherTools {

        final List<String> cities = new CopyOnWriteArrayList<>();

        @Tool("Returns the weather in the given city")
        String getWeather(@P(name = "city", value = "the city") String city) {
            cities.add(city);
            return "sunny";
        }
    }

    /**
     * Normalizes the city, as a guardrail that canonicalizes arguments would: the model has no reason to call the tool
     * again with the city it wanted.
     */
    static class RewriteCity implements ToolInputGuardrail {

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            return successWith("{\"city\":\"%s\"}".formatted(REWRITTEN_CITY));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini-2.5-flash", "gemini-3-flash-preview"})
    void should_accept_a_signed_tool_call_whose_arguments_a_guardrail_rewrote(String modelName) {

        // given
        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder());

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(modelName)
                .thinkingConfig(
                        GeminiThinkingConfig.builder().includeThoughts(true).build())
                .returnThinking(true)
                .sendThinking(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        WeatherTools tools = new WeatherTools();
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .tools(tools)
                .toolInputGuardrails(new RewriteCity())
                .build();

        // when
        String answer = assistant.chat("What is the weather in Munich? Use the tool.");

        // then
        assertThat(answer).isNotBlank();
        assertThat(tools.cities).isNotEmpty().allMatch(REWRITTEN_CITY::equals);

        AiMessage toolCallMessage = chatMemory.messages().stream()
                .filter(AiMessage.class::isInstance)
                .map(AiMessage.class::cast)
                .filter(AiMessage::hasToolExecutionRequests)
                .findFirst()
                .orElseThrow();
        assertThat(toolCallMessage.toolExecutionRequests())
                .first()
                .extracting(ToolExecutionRequest::arguments)
                .asString()
                .contains(REWRITTEN_CITY);

        String signature = toolCallMessage.attribute("thinking_signature", String.class);
        assertThat(signature).isNotBlank();

        // the request after the tool call carried the rewritten call together with its thought signature
        assertThat(spyingHttpClient.requests()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(spyingHttpClient.requests().get(1).body())
                .contains("\"thoughtSignature\"")
                .contains(REWRITTEN_CITY);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
