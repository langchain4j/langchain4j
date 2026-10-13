package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NonAiAgentBuilderTest {

    private static final Map<String, Double> EXCHANGE_RATES_TO_USD = Map.of("USD", 1.0, "EUR", 1.15);

    private static final String EXCHANGE_DESCRIPTION =
            "A money exchanger that converts a given amount of money from the original to the target currency";

    public static class ExchangeResult implements TypedKey<Double> {}

    private static AgenticServices.NonAiAgentBuilder<Double> exchangeOperatorBuilder() {
        return AgenticServices.nonAiAgentBuilder(scope -> {
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
                .outputKey("exchange");
    }

    private static UntypedAgent sequenceOf(Object agent, String outputKey) {
        return AgenticServices.sequenceBuilder()
                .subAgents(agent)
                .outputKey(outputKey)
                .build();
    }

    @Test
    void declared_inputs_are_converted_to_their_type_before_invoking_the_function() {
        UntypedAgent sequence = sequenceOf(exchangeOperatorBuilder().build(), "exchange");

        ResultWithAgenticScope<String> result = sequence.invokeWithAgenticScope(
                Map.of("originalCurrency", "EUR", "amount", "100", "targetCurrency", "USD"));

        // the function reads amount as a Double, which would fail if it were still the String "100"
        assertThat(result.agenticScope().readState("amount")).isEqualTo(100.0);
        assertThat(result.agenticScope().readState("exchange", 0.0)).isCloseTo(115.0, offset(0.001));
    }

    @Test
    void configured_metadata_is_exposed_as_agent_instance() {
        UntypedAgent sequence =
                sequenceOf(exchangeOperatorBuilder().outputType(Double.class).build(), "exchange");

        AgentInstance agent = ((AgentInstance) sequence).subagents().get(0);

        assertThat(agent.name()).isEqualTo("exchange");
        assertThat(agent.description()).isEqualTo(EXCHANGE_DESCRIPTION);
        assertThat(agent.outputKey()).isEqualTo("exchange");
        assertThat(agent.outputType()).isEqualTo(Double.class);
        assertThat(agent.async()).isFalse();
        assertThat(agent.topology()).isEqualTo(AgenticSystemTopology.NON_AI_AGENT);
        assertThat(agent.arguments())
                .containsExactly(
                        new AgentArgument(String.class, "originalCurrency"),
                        new AgentArgument(Double.class, "amount"),
                        new AgentArgument(String.class, "targetCurrency"));
    }

    @Test
    void unconfigured_metadata_falls_back_to_defaults() {
        UntypedAgent sequence =
                sequenceOf(AgenticServices.nonAiAgentBuilder(scope -> "done").build(), "result");

        AgentInstance agent = ((AgentInstance) sequence).subagents().get(0);

        assertThat(agent.name()).isEqualTo("accept");
        assertThat(agent.description()).isEmpty();
        assertThat(agent.outputKey()).isNull();
        assertThat(agent.outputType()).isEqualTo(Object.class);
        assertThat(agent.arguments()).isEmpty();
    }

    @Test
    void typed_output_key_is_resolved_to_its_name() {
        AgenticScopeFunction<Double> exchangeOperator =
                exchangeOperatorBuilder().outputKey(ExchangeResult.class).build();

        assertThat(exchangeOperator.outputKey()).isEqualTo("ExchangeResult");

        Object result = sequenceOf(exchangeOperator, "ExchangeResult")
                .invoke(Map.of("originalCurrency", "USD", "amount", 100.0, "targetCurrency", "EUR"));

        assertThat((Double) result).isCloseTo(86.956, offset(0.001));
    }

    @Test
    void missing_input_fails_without_invoking_the_function() {
        AtomicBoolean invoked = new AtomicBoolean();
        AgenticScopeFunction<String> agent = AgenticServices.nonAiAgentBuilder(scope -> {
                    invoked.set(true);
                    return "invoked";
                })
                .inputKey(String.class, "required")
                .build();

        UntypedAgent sequence = sequenceOf(agent, "result");

        assertThatThrownBy(() -> sequence.invoke(Map.of("other", "value")))
                .isInstanceOf(MissingArgumentException.class)
                .hasMessageContaining("required");
        assertThat(invoked).isFalse();
    }

    @Test
    void listener_is_notified_with_resolved_inputs_and_output() {
        AtomicReference<Map<String, Object>> inputs = new AtomicReference<>();
        AtomicReference<Object> output = new AtomicReference<>();

        AgenticScopeFunction<Double> exchangeOperator = exchangeOperatorBuilder()
                .listener(new AgentListener() {
                    @Override
                    public void beforeAgentInvocation(AgentRequest agentRequest) {
                        inputs.set(agentRequest.inputs());
                    }

                    @Override
                    public void afterAgentInvocation(AgentResponse agentResponse) {
                        output.set(agentResponse.output());
                    }
                })
                .build();

        sequenceOf(exchangeOperator, "exchange")
                .invoke(Map.of("originalCurrency", "EUR", "amount", 100, "targetCurrency", "USD"));

        assertThat(inputs.get())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("originalCurrency", "EUR", "amount", 100.0, "targetCurrency", "USD"));
        assertThat((Double) output.get()).isCloseTo(115.0, offset(0.001));
    }

    @Test
    void multiple_listeners_are_composed() {
        List<String> notifiedListeners = new ArrayList<>();

        AgenticScopeFunction<Double> exchangeOperator = exchangeOperatorBuilder()
                .listener(notifyingListener("first", notifiedListeners))
                .listener(notifyingListener("second", notifiedListeners))
                .listener(notifyingListener("third", notifiedListeners))
                .build();

        sequenceOf(exchangeOperator, "exchange")
                .invoke(Map.of("originalCurrency", "EUR", "amount", 100.0, "targetCurrency", "USD"));

        // ComposedAgentListener doesn't guarantee any notification order
        assertThat(notifiedListeners).containsExactlyInAnyOrder("first", "second", "third");
    }

    private static AgentListener notifyingListener(String name, List<String> notifiedListeners) {
        return new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                notifiedListeners.add(name);
            }
        };
    }

    @Test
    void async_agent_is_executed_on_another_thread() {
        AtomicReference<Thread> executingThread = new AtomicReference<>();
        AgenticScopeFunction<String> agent = AgenticServices.nonAiAgentBuilder(scope -> {
                    executingThread.set(Thread.currentThread());
                    return "done";
                })
                .outputKey("result")
                .async(true)
                .build();

        Object result = sequenceOf(agent, "result").invoke(Map.of());

        assertThat(result).isEqualTo("done");
        assertThat(executingThread.get()).isNotNull().isNotSameAs(Thread.currentThread());
    }
}
