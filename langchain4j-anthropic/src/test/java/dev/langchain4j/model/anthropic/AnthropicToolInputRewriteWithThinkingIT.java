package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;

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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * A tool input guardrail can rewrite the arguments of a tool call, and the rewritten call replaces the original one in
 * the chat memory. With extended thinking, Anthropic returns a signed thinking block with the tool call, and
 * LangChain4j sends it back with that call. This checks that Anthropic accepts the signed turn whose tool call
 * arguments a guardrail rewrote.
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicToolInputRewriteWithThinkingIT {

    private static final int THINKING_BUDGET_TOKENS = 1024;
    // a value the model does not produce for "Munich" on its own, so the rewrite is visible
    private static final String REWRITTEN_CITY = "Munich (normalized)";

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

        final List<String> originalArguments = new CopyOnWriteArrayList<>();

        @Override
        public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
            originalArguments.add(request.toolExecutionRequest().arguments());
            return successWith("{\"city\":\"%s\"}".formatted(REWRITTEN_CITY));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = AnthropicChatModelName.class,
            mode = INCLUDE,
            names = {"CLAUDE_HAIKU_4_5_20251001", "CLAUDE_SONNET_4_6"})
    void should_accept_a_signed_turn_whose_tool_call_arguments_a_guardrail_rewrote(AnthropicChatModelName modelName) {

        // given
        SpyingHttpClient spyingHttpClient = new SpyingHttpClient(JdkHttpClient.builder());

        AnthropicChatModel model = AnthropicChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(spyingHttpClient))
                .baseUrl(System.getenv("ANTHROPIC_CACHING_BASE_URL"))
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(modelName)
                .thinkingType("enabled")
                .thinkingBudgetTokens(THINKING_BUDGET_TOKENS)
                .maxTokens(THINKING_BUDGET_TOKENS + 1000)
                .returnThinking(true)
                .logRequests(true)
                .logResponses(true)
                .build();

        WeatherTools tools = new WeatherTools();
        RewriteCity rewriteCity = new RewriteCity();
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(20);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .tools(tools)
                .toolInputGuardrails(rewriteCity)
                .build();

        // when
        String answer = assistant.chat("What is the weather in Munich? Use the tool.");

        // then
        assertThat(answer).isNotBlank();
        assertThat(rewriteCity.originalArguments).isNotEmpty().noneMatch(arguments -> arguments.contains(REWRITTEN_CITY));
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

        // the request after the tool call carried the signed thinking block together with the rewritten call
        assertThat(spyingHttpClient.requests()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(spyingHttpClient.requests().get(1).body())
                .contains("\"signature\"")
                .contains(REWRITTEN_CITY);
    }
}
