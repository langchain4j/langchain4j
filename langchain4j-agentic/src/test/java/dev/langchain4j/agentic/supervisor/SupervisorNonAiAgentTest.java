package dev.langchain4j.agentic.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests that a non-AI agent created with {@link AgenticServices#nonAiAgentBuilder} can be used as a subagent of a
 * supervisor, with the planning model scripted so that no real LLM is needed.
 */
class SupervisorNonAiAgentTest {

    private static final Map<String, Double> EXCHANGE_RATES_TO_USD = Map.of("USD", 1.0, "EUR", 1.15);

    private static final String EXCHANGE_DESCRIPTION =
            "A money exchanger that converts a given amount of money from the original to the target currency";

    /** Replies with the given responses in order, capturing the first system message it receives. */
    static class ScriptedChatModel implements ChatModel {

        private final Iterator<String> responses;
        private final AtomicReference<String> systemMessage = new AtomicReference<>();

        ScriptedChatModel(String... responses) {
            this.responses = List.of(responses).iterator();
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            chatRequest.messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .findFirst()
                    .ifPresent(message -> systemMessage.compareAndSet(null, ((SystemMessage) message).text()));
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(responses.next()))
                    .build();
        }

        String capturedPrompt() {
            return systemMessage.get();
        }
    }

    @Test
    void supervisor_invokes_programmatic_non_ai_agent() {
        ScriptedChatModel plannerModel = new ScriptedChatModel("""
                {"agentName": "exchange", "arguments": {"originalCurrency": "EUR", "amount": 100, "targetCurrency": "USD"}}
                """, """
                {"agentName": "done", "arguments": {"response": "100 EUR are 115 USD"}}
                """);

        AgenticScopeFunction<Double> exchangeOperator = AgenticServices.nonAiAgentBuilder(scope -> {
                    String originalCurrency = scope.readState("originalCurrency", "");
                    Double amount = scope.readState("amount", 0.0);
                    String targetCurrency = scope.readState("targetCurrency", "");
                    return amount
                            * EXCHANGE_RATES_TO_USD.get(originalCurrency)
                            / EXCHANGE_RATES_TO_USD.get(targetCurrency);
                })
                .name("exchange")
                .description(EXCHANGE_DESCRIPTION)
                .inputKeys(String.class, "originalCurrency", Double.class, "amount", String.class, "targetCurrency")
                .outputKey("exchange")
                .build();

        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.LAST)
                .subAgents(exchangeOperator)
                .build();

        ResultWithAgenticScope<String> result = supervisor.invokeWithAgenticScope("Convert 100 EUR to USD");

        assertThat(plannerModel.capturedPrompt())
                .contains("'exchange$0', '" + EXCHANGE_DESCRIPTION + "'")
                .contains("[originalCurrency: String, amount: Double, targetCurrency: String]");

        // the planner passed amount as an Integer, converted to the declared Double before the function read it
        assertThat(result.agenticScope().readState("amount")).isEqualTo(100.0);
        assertThat(result.agenticScope().readState("exchange", 0.0)).isCloseTo(115.0, offset(0.001));
    }
}
