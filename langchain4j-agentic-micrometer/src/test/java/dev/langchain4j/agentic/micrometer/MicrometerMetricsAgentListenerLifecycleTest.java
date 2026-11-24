package dev.langchain4j.agentic.micrometer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticSystemSuspendedException;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MicrometerMetricsAgentListenerLifecycleTest {

    public interface Writer {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    public interface Notifier {

        @UserMessage("Notify about {{topic}}")
        @Agent
        void notify(@V("topic") String topic);
    }

    public interface ReactiveNovel {

        @Agent(outputKey = "story")
        Flow.Publisher<String> write(@V("topic") String topic);
    }

    public interface ReactiveWriter {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        Flow.Publisher<String> write(@V("topic") String topic);
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerMetricsAgentListener listener = new MicrometerMetricsAgentListener(registry);

    @Test
    void records_a_reactive_agent_once_per_subscription_and_not_when_it_returns_its_stream() throws Exception {
        ReactiveWriter writer = AgenticServices.agentBuilder(ReactiveWriter.class)
                .name("writer")
                .streamingChatModel(
                        MockStreamingChatModel.thatStreams("a", "story").thenRepeats())
                .listener(listener)
                .build();

        Flow.Publisher<String> stream = writer.write("dragons");

        assertThat(registry.find("gen_ai.invoke_agent.duration").timers()).isEmpty();
        assertThat(activeInvocations()).isZero();
        assertThat(listener.pendingInvocations()).isZero();

        consume(stream);
        consume(stream);

        await().untilAsserted(() -> assertThat(
                        registry.find("gen_ai.invoke_agent.duration").timers())
                .extracting(Timer::count)
                .containsExactly(2L));
        Timer duration = registry.get("gen_ai.invoke_agent.duration").timer();
        assertThat(duration.getId().getTag("outcome")).isEqualTo("SUCCESS");
        assertThat(activeInvocations()).isZero();
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_nothing_for_a_reactive_stream_that_is_never_subscribed_to() {
        ReactiveWriter writer = AgenticServices.agentBuilder(ReactiveWriter.class)
                .name("writer")
                .streamingChatModel(MockStreamingChatModel.thatStreams("a", "story"))
                .listener(listener)
                .build();

        writer.write("dragons");

        assertThat(registry.find("gen_ai.invoke_agent.duration").timers()).isEmpty();
        assertThat(activeInvocations()).isZero();
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void records_a_workflow_returning_a_reactive_stream_when_it_returns_it_and_its_agent_per_subscription()
            throws Exception {
        ReactiveWriter writer = AgenticServices.agentBuilder(ReactiveWriter.class)
                .name("writer")
                .streamingChatModel(MockStreamingChatModel.thatStreams("a", "story"))
                .build();
        ReactiveNovel novel = AgenticServices.sequenceBuilder(ReactiveNovel.class)
                .name("novel")
                .subAgents(writer)
                .listener(listener)
                .outputKey("story")
                .build();

        Flow.Publisher<String> stream = novel.write("dragons");

        assertThat(registry.find("gen_ai.invoke_agent.duration").timers())
                .extracting(timer -> timer.getId().getTag("gen_ai.agent.name"), Timer::count)
                .containsExactly(tuple("novel", 1L));

        consume(stream);

        await().untilAsserted(() -> assertThat(
                        registry.find("gen_ai.invoke_agent.duration").timers())
                .extracting(timer -> timer.getId().getTag("gen_ai.agent.name"), Timer::count)
                .containsExactlyInAnyOrder(tuple("novel", 1L), tuple("writer", 1L)));
        assertThat(activeInvocations()).isZero();
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void keeps_nothing_for_the_completed_invocations_of_an_agent_without_a_result() throws Exception {
        Notifier notifier = AgenticServices.agentBuilder(Notifier.class)
                .name("notifier")
                .chatModel(MockChatModel.thatResponds("done").thenRepeats())
                .listener(listener)
                .build();

        for (int i = 0; i < 10; i++) {
            notifier.notify("dragons");
        }
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CompletableFuture.allOf(IntStream.range(0, 10)
                            .mapToObj(i -> CompletableFuture.runAsync(() -> notifier.notify("dragons"), executor))
                            .toArray(CompletableFuture[]::new))
                    .get(5, SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(registry.get("gen_ai.invoke_agent.duration").timer().count()).isEqualTo(20);
        assertThat(activeInvocations()).isZero();
        assertThat(listener.pendingInvocations()).isZero();
    }

    @Test
    void keeps_nothing_for_the_invocations_of_an_agent_without_a_result_whose_tags_cannot_be_computed() {
        MicrometerMetricsAgentListener taggingListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(agenticScope -> {
                    throw new IllegalStateException("no tenant");
                })
                .build();
        Notifier notifier = AgenticServices.agentBuilder(Notifier.class)
                .name("notifier")
                .chatModel(MockChatModel.thatResponds("done").thenRepeats())
                .listener(taggingListener)
                .build();

        for (int i = 0; i < 10; i++) {
            notifier.notify("dragons");
        }

        assertThat(registry.getMeters()).isEmpty();
        assertThat(taggingListener.pendingInvocations()).isZero();
    }

    @Test
    void records_the_workflow_and_not_its_agent_whose_tags_cannot_be_computed() {
        AtomicInteger calls = new AtomicInteger();
        MicrometerMetricsAgentListener taggingListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(agenticScope -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new IllegalStateException("no tenant");
                    }
                    return Tags.of("tenant", "acme");
                })
                .build();
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story"))
                .build();

        AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(writer)
                .listener(taggingListener)
                .outputKey("story")
                .build()
                .invoke(Map.of("topic", "dragons"));

        assertThat(calls).hasValue(2);
        assertThat(registry.find("gen_ai.invoke_agent.duration").timers())
                .extracting(timer -> timer.getId().getTag("gen_ai.agent.name"), Timer::count)
                .containsExactly(tuple("novel", 1L));
        assertThat(registry.get("gen_ai.invoke_agent.duration").timer().getId().getTag("tenant"))
                .isEqualTo("acme");
        assertThat(registry.getMeters())
                .allSatisfy(meter ->
                        assertThat(meter.getId().getTag("gen_ai.agent.name")).isNotEqualTo("writer"));
        assertThat(taggingListener.pendingInvocations()).isZero();
    }

    @Test
    void records_the_agent_and_not_its_workflow_whose_tags_cannot_be_computed() {
        AtomicInteger calls = new AtomicInteger();
        MicrometerMetricsAgentListener taggingListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(agenticScope -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("no tenant");
                    }
                    return Tags.of("tenant", "acme");
                })
                .build();

        AgenticServices.sequenceBuilder()
                .name("novel")
                .subAgents(AgenticServices.agentBuilder(Writer.class)
                        .name("writer")
                        .chatModel(MockChatModel.thatResponds("a story"))
                        .build())
                .listener(taggingListener)
                .outputKey("story")
                .build()
                .invoke(Map.of("topic", "dragons"));

        assertThat(registry.find("gen_ai.invoke_agent.duration").timers())
                .extracting(timer -> timer.getId().getTag("gen_ai.agent.name"), Timer::count)
                .containsExactly(tuple("writer", 1L));
        assertThat(taggingListener.pendingInvocations()).isZero();
    }

    @Test
    void records_nothing_for_a_suspended_workflow_whose_tags_cannot_be_computed() {
        AtomicInteger calls = new AtomicInteger();
        MicrometerMetricsAgentListener taggingListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(agenticScope -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("no tenant");
                    }
                    return Tags.of("tenant", "acme");
                })
                .build();

        assertThatThrownBy(() -> AgenticServices.sequenceBuilder()
                        .name("review")
                        .subAgents(AgenticServices.humanInTheLoopBuilder()
                                .description("Human approval")
                                .outputKey("approval")
                                .responseProvider(agenticScope -> new SuspendedResponse<>("approval"))
                                .build())
                        .listener(taggingListener)
                        .outputKey("approval")
                        .build()
                        .invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgenticSystemSuspendedException.class);

        assertThat(registry.find("gen_ai.invoke_agent.duration")
                        .tag("gen_ai.agent.name", "review")
                        .timers())
                .isEmpty();
        assertThat(taggingListener.pendingInvocations()).isZero();
    }

    @Test
    void records_nothing_for_an_agent_whose_tags_cannot_be_computed_and_computes_them_once() {
        AtomicInteger calls = new AtomicInteger();
        MicrometerMetricsAgentListener taggingListener = MicrometerMetricsAgentListener.builder()
                .meterRegistry(registry)
                .tags(agenticScope -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("no tenant");
                    }
                    return Tags.of("tenant", "acme");
                })
                .build();
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story"))
                .listener(taggingListener)
                .build();

        writer.write("dragons");

        assertThat(calls).hasValue(1);
        assertThat(registry.find("gen_ai.invoke_agent.duration").timers()).isEmpty();
        assertThat(taggingListener.pendingInvocations()).isZero();
    }

    @Test
    void counts_the_failure_of_an_agent_that_failed_before_being_invoked() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.memoryId()).thenReturn("memory");
        AgentInstance parent = mock(AgentInstance.class);
        when(parent.name()).thenReturn("novel");
        when(parent.agentId()).thenReturn("novel");
        AgentInstance mcpAgent = agent("lookup", AgenticSystemTopology.NON_AI_AGENT, parent);
        AgentInstance llmAgent = agent("writer", AgenticSystemTopology.AI_AGENT, parent);

        listener.onAgentInvocationError(new AgentInvocationError(
                agenticScope, mcpAgent, Map.of(), new AgentInvocationException("no tool", new RuntimeException())));
        listener.onAgentInvocationError(new AgentInvocationError(
                agenticScope, llmAgent, Map.of(), new AgentInvocationException("model down", new RuntimeException())));

        assertThat(registry.get("langchain4j.agent.invocations")
                        .tags(
                                "gen_ai.agent.name",
                                "lookup",
                                "langchain4j.agent.parent.name",
                                "novel",
                                "outcome",
                                "ERROR")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.find("langchain4j.agent.invocations")
                        .tag("gen_ai.agent.name", "writer")
                        .counters())
                .isEmpty();
        assertThat(registry.find("gen_ai.invoke_agent.duration").timers()).isEmpty();
    }

    @Test
    void registers_again_the_meters_removed_from_the_registry() {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .name("writer")
                .chatModel(MockChatModel.thatResponds("a story").thenRepeats())
                .listener(listener)
                .build();
        writer.write("dragons");
        Meter duration = registry.get("gen_ai.invoke_agent.duration").timer();

        registry.remove(duration);
        writer.write("dragons");

        assertThat(registry.get("gen_ai.invoke_agent.duration").timer().count()).isEqualTo(1);

        registry.clear();
        writer.write("dragons");

        assertThat(registry.get("gen_ai.invoke_agent.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("gen_ai.invoke_agent.inference_calls").summary().count())
                .isEqualTo(1);
    }

    @Test
    void registers_again_a_removed_meter_that_several_agents_share_through_a_meter_filter() {
        registry.config().meterFilter(MeterFilter.ignoreTags("gen_ai.agent.name"));
        MicrometerMetricsAgentListener sharingListener = new MicrometerMetricsAgentListener(registry);
        Writer first = writer("first", sharingListener);
        Writer second = writer("second", sharingListener);
        first.write("dragons");
        second.write("dragons");

        registry.clear();
        first.write("dragons");
        second.write("dragons");

        assertThat(registry.get("gen_ai.invoke_agent.duration").timer().count()).isEqualTo(2);
    }

    private static Writer writer(String name, MicrometerMetricsAgentListener listener) {
        return AgenticServices.agentBuilder(Writer.class)
                .name(name)
                .chatModel(MockChatModel.thatResponds("a story").thenRepeats())
                .listener(listener)
                .build();
    }

    private static AgentInstance agent(String name, AgenticSystemTopology topology, AgentInstance parent) {
        AgentInstance agent = mock(AgentInstance.class);
        when(agent.name()).thenReturn(name);
        when(agent.agentId()).thenReturn(name + "$0");
        when(agent.topology()).thenReturn(topology);
        when(agent.parent()).thenReturn(parent);
        when(parent.subagents()).thenReturn(List.of());
        return agent;
    }

    private long activeInvocations() {
        return registry.find("langchain4j.agent.invocations.active").longTaskTimers().stream()
                .mapToLong(LongTaskTimer::activeTasks)
                .sum();
    }

    private static void consume(Flow.Publisher<String> stream) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        stream.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable error) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertThat(done.await(5, SECONDS)).isTrue();
    }
}
