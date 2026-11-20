package dev.langchain4j.agentic.micrometer;

import static dev.langchain4j.test.mock.MockChatModel.toolCall;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.micrometer.MicrometerMetricsAgentListenerTest.BrokenFacts;
import dev.langchain4j.agentic.micrometer.MicrometerMetricsAgentListenerTest.Facts;
import dev.langchain4j.agentic.micrometer.MicrometerMetricsAgentListenerTest.StreamingWriter;
import dev.langchain4j.agentic.micrometer.MicrometerMetricsAgentListenerTest.Writer;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class MicrometerMetricsAgentListenerPrometheusTest {

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final List<String> registrationFailures = new CopyOnWriteArrayList<>();
    private final MicrometerMetricsAgentListener listener = MicrometerMetricsAgentListener.builder()
            .meterRegistry(registry)
            .tags(agenticScope -> Tags.of("tenant", "acme"))
            .build();

    @Test
    void exports_every_meter_with_one_set_of_labels_whatever_the_outcome() {
        registry.config().onMeterRegistrationFailed((id, reason) -> registrationFailures.add(id + ": " + reason));
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().equals("gen_ai.invoke_agent.duration")) {
                    return DistributionStatisticConfig.builder()
                            .serviceLevelObjectives(
                                    Duration.ofMillis(100).toNanos(),
                                    Duration.ofSeconds(1).toNanos())
                            .build()
                            .merge(config);
                }
                return config;
            }
        });

        AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(writer("writer", new Facts()), summarizer())
                .listener(listener)
                .outputKey("story")
                .build()
                .invoke(Map.of("topic", "dragons"));
        assertThatThrownBy(() -> AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(AgenticServices.agentBuilder(Writer.class)
                        .name("writer")
                        .chatModel(MockChatModel.thatResponds(
                                AiMessage.from(toolCall("lookup", Map.of())), AiMessage.from("a story")))
                        .tools(new BrokenFacts())
                        .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocation())
                        .build())
                .listener(listener)
                .outputKey("story")
                .build()
                .invoke(Map.of("topic", "dragons")));
        assertThatThrownBy(() -> AgenticServices.sequenceBuilder()
                        .name("novel")
                        .subAgents(
                                writer("writer", new Facts()),
                                AgenticServices.humanInTheLoopBuilder()
                                        .description("Human approval")
                                        .outputKey("approval")
                                        .responseProvider(agenticScope -> new SuspendedResponse<>("approval"))
                                        .build())
                        .listener(listener)
                        .outputKey("story")
                        .build()
                        .invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgenticSystemSuspendedException.class);
        AgenticServices.agentBuilder(StreamingWriter.class)
                .name("writer")
                .streamingChatModel(MockStreamingChatModel.thatStreams("a", "b", "c"))
                .listener(listener)
                .build()
                .write("dragons")
                .onPartialResponseWithContext(
                        (partial, context) -> context.streamingHandle().cancel())
                .onError(error -> {})
                .start();
        await().atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(registry.scrape()).contains("outcome=\"CANCELLED\""));

        String scrape = registry.scrape();

        assertThat(registrationFailures).isEmpty();
        assertThat(scrape)
                .contains(
                        "# TYPE gen_ai_invoke_agent_duration_seconds histogram",
                        "gen_ai_invoke_agent_inference_calls",
                        "gen_ai_invoke_agent_tool_calls",
                        "# TYPE gen_ai_execute_tool_duration_seconds",
                        "langchain4j_agent_token_usage",
                        "langchain4j_agent_invocations_total",
                        "langchain4j_agent_invocations_active",
                        "gen_ai_invoke_agent_duration_seconds_count{error_type=\"none\",gen_ai_agent_name=\"novel\","
                                + "gen_ai_operation_name=\"invoke_workflow\",outcome=\"SUCCESS\",tenant=\"acme\"} 1",
                        "outcome=\"ERROR\"",
                        "outcome=\"SUSPENDED\"",
                        "outcome=\"CANCELLED\"",
                        "le=\"0.1\"");
    }

    private static Writer writer(String name, Object tools) {
        return AgenticServices.agentBuilder(Writer.class)
                .name(name)
                .chatModel(MockChatModel.thatResponds(
                        AiMessage.from(toolCall("lookup", Map.of())), AiMessage.from("a story")))
                .tools(tools)
                .build();
    }

    private static Writer summarizer() {
        return AgenticServices.agentBuilder(Writer.class)
                .name("summarizer")
                .chatModel(MockChatModel.thatResponds(ChatResponse.builder()
                        .aiMessage(AiMessage.from("a summary"))
                        .tokenUsage(new TokenUsage(10, 2))
                        .build()))
                .build();
    }
}
