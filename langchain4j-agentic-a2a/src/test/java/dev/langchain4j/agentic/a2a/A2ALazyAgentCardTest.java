package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AClientCustomizer;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.V;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.spec.A2AClientError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The agent card of an A2A client agent is fetched lazily, on the first invocation, so that building an agentic
 * system never depends on the availability of the remote agent (issue #6922). The name of the agent is always known
 * locally, the description and the auto-detected tenant come from the card when first needed, a failed fetch is
 * retried, and concurrent first invocations fetch the card once. The fetch runs on a thread of the LangChain4j
 * executor within the resolution timeout of the agent, and the invocations waiting on it all fail when it fails
 * (issue #6958).
 */
@Timeout(60)
class A2ALazyAgentCardTest {

    /** A loopback URL on a port nobody listens on, so that a real card fetch fails fast with connection refused. */
    static final String UNREACHABLE_A2A_SERVER_URL = "http://localhost:" + closedPort();

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final AgentCard REMOTE_FIXER_CARD = card("remote-fixer", "Fixes the analyzed problem");

    private static AgentCard card(String name, String description, AgentInterface... supportedInterfaces) {
        return AgentCard.builder()
                .name(name)
                .description(description)
                .version("1.0.0")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(supportedInterfaces))
                .build();
    }

    /**
     * A card source that counts its fetches and can be told to fail, to be slow, or to wait on a gate, failing like
     * the card resolver of the SDK when it is interrupted there.
     */
    static class CardSource implements Supplier<AgentCard> {

        final AgentCard card;
        final AtomicInteger fetches = new AtomicInteger();
        final CountDownLatch interrupted = new CountDownLatch(1);
        volatile boolean failing;
        volatile boolean ignoringInterrupt;
        volatile long delayMillis;
        volatile CountDownLatch gate;
        volatile Thread fetchingThread;

        CardSource(AgentCard card) {
            this.card = card;
        }

        @Override
        public AgentCard get() {
            fetches.incrementAndGet();
            fetchingThread = Thread.currentThread();
            CountDownLatch gate = this.gate;
            if (gate != null) {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    if (!ignoringInterrupt) {
                        throw new A2AClientError("Failed to obtain agent card", e);
                    }
                    awaitUninterruptibly(gate);
                }
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failing) {
                throw new A2AClientError("Failed to obtain agent card", new IOException("connection refused"));
            }
            return card;
        }

        private static void awaitUninterruptibly(CountDownLatch gate) {
            while (true) {
                try {
                    gate.await();
                    return;
                } catch (InterruptedException ignored) {
                    // like a fetch blocked where the interruption of its thread is not noticed
                }
            }
        }
    }

    /** A mocked client replying the given text to every message and capturing the parameters it was sent. */
    static class ReplyingClient {

        final Client client = mock(Client.class);
        final List<MessageSendParams> sentParams = new CopyOnWriteArrayList<>();

        ReplyingClient(String reply, AgentCard card) {
            Message agentResponse = Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart(reply)))
                    .build();
            doAnswer(invocation -> {
                        reply(invocation.getArgument(1), agentResponse, card);
                        return null;
                    })
                    .when(client)
                    .sendMessage(any(Message.class), anyList(), any(), any());
            doAnswer(invocation -> {
                        sentParams.add(invocation.getArgument(0, MessageSendParams.class));
                        reply(invocation.getArgument(1), agentResponse, card);
                        return null;
                    })
                    .when(client)
                    .sendMessage(any(MessageSendParams.class), anyList(), any(), any());
        }

        private static void reply(
                List<BiConsumer<ClientEvent, AgentCard>> consumers, Message response, AgentCard card) {
            consumers.get(0).accept(new MessageEvent(response), card);
        }
    }

    /** A planner model that immediately reports the task as done. */
    private static ChatModelMock donePlannerModel() {
        return ChatModelMock.thatAlwaysResponds("""
                {"agentName": "done", "arguments": {"response": "nothing to fix"}}
                """);
    }

    private static String systemMessage(ChatModelMock plannerModel) {
        return systemMessage(plannerModel, 0);
    }

    private static String systemMessage(ChatModelMock plannerModel, int request) {
        return plannerModel.requests().get(request).messages().stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElseThrow();
    }

    /**
     * Installs an A2A service whose builders take the card lazily from the given source and the client from the
     * given factory, or build a real client from the card when the factory is null.
     */
    private void useLazyA2AService(CardSource cardSource, Function<AgentCard, Client> clientFactory) {
        A2AService.setA2AService(new DefaultA2AService() {
            @Override
            public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, String tenant, Class<T> agentServiceClass) {
                return new DefaultA2AClientBuilder<>(a2aServerUrl, cardSource, agentServiceClass, clientFactory)
                        .tenant(tenant);
            }
        });
    }

    private static Function<AgentCard, Client> replying(String reply) {
        return card -> new ReplyingClient(reply, card).client;
    }

    @AfterEach
    void restoreDefaultA2AService() {
        A2AService.setA2AService(new DefaultA2AService());
    }

    private static <E extends Throwable> E causeOfType(Throwable throwable, Class<E> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " in the cause chain of " + throwable);
    }

    private static boolean hasCauseOfType(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    public static class Analyzer {

        @Agent(description = "Analyzes the request", outputKey = "analysis")
        public static String analyze(@V("request") String request) {
            return "analysis of " + request;
        }
    }

    public interface FixAgent {

        @A2AClientAgent(outputKey = "fix")
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return UNREACHABLE_A2A_SERVER_URL;
        }
    }

    public interface Pipeline {

        @SequenceAgent(
                outputKey = "fix",
                subAgents = {Analyzer.class, FixAgent.class})
        String process(@V("request") String request);
    }

    public interface NeverActivatedFixAgent {

        @A2AClientAgent(outputKey = "fix")
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return UNREACHABLE_A2A_SERVER_URL;
        }

        @ActivationCondition
        static boolean never(AgenticScope scope) {
            return false;
        }
    }

    public interface PipelineWithOptionalFix {

        @SequenceAgent(
                outputKey = "analysis",
                subAgents = {Analyzer.class, NeverActivatedFixAgent.class})
        String process(@V("request") String request);
    }

    public interface NamedFixAgent {

        @A2AClientAgent(name = "fixer", value = "Applies a fix to the analyzed problem", outputKey = "fix")
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return UNREACHABLE_A2A_SERVER_URL;
        }
    }

    public interface ImpatientFixAgent {

        @A2AClientAgent(outputKey = "fix", resolutionTimeoutSeconds = 1)
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return UNREACHABLE_A2A_SERVER_URL;
        }
    }

    public interface ImpatientPipeline {

        @SequenceAgent(
                outputKey = "fix",
                subAgents = {Analyzer.class, ImpatientFixAgent.class})
        String process(@V("request") String request);
    }

    public interface FixAgentWithoutTimeout {

        @A2AClientAgent(outputKey = "fix", resolutionTimeoutSeconds = 0)
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return UNREACHABLE_A2A_SERVER_URL;
        }
    }

    public interface FixAgentWithNegativeTimeout {

        @A2AClientAgent(outputKey = "fix", resolutionTimeoutSeconds = -2)
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return UNREACHABLE_A2A_SERVER_URL;
        }
    }

    /** A typed agent for the programmatic builder. */
    public interface Fixer {

        @Agent
        String fix(@V("analysis") String analysis);
    }

    public interface DescribedFixer {

        @Agent(name = "fixer", description = "Applies a fix to the analyzed problem")
        String fix(@V("analysis") String analysis);
    }

    @Test
    void building_a_workflow_does_not_require_the_remote_agent_to_be_up() {
        // the issue's reproducer: the card used to be fetched here, failing the whole pipeline
        Pipeline pipeline = AgenticServices.createAgenticSystem(Pipeline.class, (ChatModel) null);

        Throwable failure = catchThrowable(() -> pipeline.process("a broken build"));

        A2AAgentCardException cardException = causeOfType(failure, A2AAgentCardException.class);
        assertThat(cardException.agentName()).isEqualTo("applyFix");
        assertThat(cardException.a2aServerUrl()).isEqualTo(UNREACHABLE_A2A_SERVER_URL);
        assertThat(cardException)
                .hasMessageContaining("applyFix")
                .hasMessageContaining(UNREACHABLE_A2A_SERVER_URL)
                .hasCauseInstanceOf(A2AClientError.class);
    }

    @Test
    void an_error_handler_can_skip_an_unreachable_remote_agent() {
        FixAgent fixAgent = AgenticServices.createAgenticSystem(FixAgent.class, (ChatModel) null);
        var analyzer = AgenticServices.nonAiAgentBuilder(scope -> "analysis of " + scope.readState("request", ""))
                .name("analyzer")
                .description("Analyzes the request")
                .outputKey("analysis")
                .build();
        UntypedAgent pipeline = AgenticServices.sequenceBuilder()
                .subAgents(analyzer, fixAgent)
                .outputKey("fix")
                .errorHandler(errorContext -> hasCauseOfType(errorContext.exception(), A2AAgentCardException.class)
                        ? ErrorRecoveryResult.result("no fix available, the remote fixer is unreachable")
                        : ErrorRecoveryResult.throwException())
                .build();

        assertThat(pipeline.invoke(Map.of("request", "a broken build")))
                .isEqualTo("no fix available, the remote fixer is unreachable");
    }

    @Test
    void a_malformed_a2a_server_url_still_fails_when_the_agent_is_built() {
        assertThatThrownBy(() -> AgenticServices.a2aBuilder("::not-a-url::", Fixer.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("::not-a-url::");
        assertThatThrownBy(() -> AgenticServices.a2aBuilder("localhost:8080", Fixer.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("localhost:8080")
                .hasMessageContaining("http or https");
    }

    @Test
    void a_never_activated_a2a_subagent_never_fetches_the_card() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));
        PipelineWithOptionalFix pipeline =
                AgenticServices.createAgenticSystem(PipelineWithOptionalFix.class, (ChatModel) null);

        assertThat(pipeline.process("a broken build")).isEqualTo("analysis of a broken build");
        assertThat(cardSource.fetches).hasValue(0);
    }

    @Test
    void the_card_is_fetched_once_across_concurrent_invocations() throws Exception {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.delayMillis = 200;
        AtomicInteger clientCreations = new AtomicInteger();
        useLazyA2AService(cardSource, card -> {
            clientCreations.incrementAndGet();
            return new ReplyingClient("fixed", card).client;
        });
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        int invocations = 8;
        CyclicBarrier start = new CyclicBarrier(invocations);
        ExecutorService executor = Executors.newFixedThreadPool(invocations);
        try {
            Callable<String> invocation = () -> {
                start.await(10, TimeUnit.SECONDS);
                return fixer.fix("a broken build");
            };
            List<Future<String>> results = executor.invokeAll(Collections.nCopies(invocations, invocation));
            for (Future<String> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("fixed");
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(cardSource.fetches).hasValue(1);
        assertThat(clientCreations).hasValue(1);
    }

    @Test
    void a_failed_fetch_is_retried_on_the_next_invocation() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.failing = true;
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        // an A2AAgentCardException is an AgentUnavailableException, the type common to every integration
        assertThatThrownBy(() -> fixer.fix("a broken build"))
                .isInstanceOf(A2AAgentCardException.class)
                .isInstanceOf(AgentUnavailableException.class)
                .hasMessageContaining("'fix'")
                .hasMessageContaining("http://remote-host:8080")
                .hasCauseInstanceOf(A2AClientError.class);

        cardSource.failing = false;
        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");
        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");
        assertThat(cardSource.fetches).hasValue(2);
    }

    @Test
    void a_fetch_that_never_completes_fails_after_the_resolution_timeout() throws Exception {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.gate = new CountDownLatch(1);
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .resolutionTimeout(Duration.ofMillis(200))
                .outputKey("fix")
                .build();

        assertThatThrownBy(() -> fixer.fix("a broken build"))
                .isInstanceOf(A2AAgentCardException.class)
                .isInstanceOf(AgentUnavailableException.class)
                .hasMessageContaining("'fix'")
                .hasMessageEndingWith("http://remote-host:8080 within 200 ms")
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(cardSource.interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        // once the interrupted fetch has ended, the next invocation fetches the card again
        cardSource.gate = null;
        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(A2AAgentCardException.class)
                .untilAsserted(() -> assertThat(fixer.fix("a broken build")).isEqualTo("fixed"));
        assertThat(cardSource.fetches).hasValue(2);
    }

    @Test
    void an_invocation_arriving_while_a_fetch_that_timed_out_is_still_running_fails_at_once_saying_so()
            throws Exception {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        CountDownLatch gate = new CountDownLatch(1);
        cardSource.gate = gate;
        cardSource.ignoringInterrupt = true;
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .resolutionTimeout(Duration.ofMillis(200))
                .outputKey("fix")
                .build();

        assertThatThrownBy(() -> fixer.fix("a broken build"))
                .isInstanceOf(A2AAgentCardException.class)
                .hasMessageEndingWith("http://remote-host:8080 within 200 ms");
        assertThat(cardSource.interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        // the card is not fetched a second time while the first fetch is running, and the failure does not read as
        // if the server had been given the timeout again
        assertThatThrownBy(() -> fixer.fix("a broken build"))
                .isInstanceOf(A2AAgentCardException.class)
                .hasMessageContaining("'fix'")
                .hasMessageEndingWith(
                        "http://remote-host:8080: the previous fetch is still running after its timeout of 200 ms")
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(cardSource.fetches).hasValue(1);

        // the card that the first fetch returns in the end is kept
        gate.countDown();
        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(A2AAgentCardException.class)
                .untilAsserted(() -> assertThat(fixer.fix("a broken build")).isEqualTo("fixed"));
        assertThat(cardSource.fetches).hasValue(1);
    }

    @Test
    void concurrent_invocations_share_the_failure_of_a_single_fetch() throws Exception {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.failing = true;
        cardSource.gate = new CountDownLatch(1);
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Thread> callers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread caller = new Thread(() -> failures.add(catchThrowable(() -> fixer.fix("a broken build"))));
            caller.start();
            callers.add(caller);
        }
        // every invocation is waiting on the fetch started by the first one when that fetch fails
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> callers.stream().allMatch(caller -> caller.getState() == Thread.State.WAITING));
        cardSource.gate.countDown();
        for (Thread caller : callers) {
            caller.join(10_000);
        }

        assertThat(failures).hasSize(8).doesNotHaveDuplicates().allSatisfy(failure -> assertThat(failure)
                .isInstanceOf(A2AAgentCardException.class)
                .hasCauseInstanceOf(A2AClientError.class));
        assertThat(failures.stream().map(Throwable::getCause).distinct()).hasSize(1);
        assertThat(cardSource.fetches).hasValue(1);

        // the failure is not cached: the next invocation fetches the card again
        cardSource.failing = false;
        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");
        assertThat(cardSource.fetches).hasValue(2);
    }

    @Test
    void the_card_is_fetched_on_a_thread_of_the_executor_and_the_client_is_created_by_the_caller() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        AtomicReference<Thread> clientCreationThread = new AtomicReference<>();
        useLazyA2AService(cardSource, card -> {
            clientCreationThread.set(Thread.currentThread());
            return new ReplyingClient("fixed", card).client;
        });
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");

        assertThat(cardSource.fetchingThread).isNotNull().isNotSameAs(Thread.currentThread());
        assertThat(clientCreationThread).hasValue(Thread.currentThread());
    }

    @Test
    void an_invocation_interrupted_while_another_one_creates_the_client_does_not_report_a_failed_fetch()
            throws Exception {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        CountDownLatch creating = new CountDownLatch(1);
        CountDownLatch gate = new CountDownLatch(1);
        useLazyA2AService(cardSource, card -> {
            creating.countDown();
            try {
                gate.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return new ReplyingClient("fixed", card).client;
        });
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        AtomicReference<String> fix = new AtomicReference<>();
        Thread creator = new Thread(() -> fix.set(fixer.fix("a broken build")));
        creator.start();
        assertThat(creating.await(10, TimeUnit.SECONDS)).isTrue();

        // the card is fetched by now: the second invocation only waits for the client that the first one creates
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread waiter = new Thread(() -> failure.set(catchThrowable(() -> fixer.fix("a broken build"))));
        waiter.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> waiter.getState() == Thread.State.WAITING);
        waiter.interrupt();
        waiter.join(10_000);

        assertThat(failure.get())
                .isInstanceOf(AgentUnavailableException.class)
                .isNotInstanceOf(A2AAgentCardException.class)
                .hasMessage("Interrupted while waiting for the client of A2A agent 'fix' to be created")
                .hasCauseInstanceOf(InterruptedException.class);

        gate.countDown();
        creator.join(10_000);
        assertThat(fix).hasValue("fixed");
        assertThat(cardSource.fetches).hasValue(1);
    }

    @Test
    void the_resolution_timeout_of_the_annotation_is_applied() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.gate = new CountDownLatch(1);
        useLazyA2AService(cardSource, replying("fixed"));
        ImpatientPipeline pipeline = AgenticServices.createAgenticSystem(ImpatientPipeline.class, (ChatModel) null);

        Throwable failure = catchThrowable(() -> pipeline.process("a broken build"));

        assertThat(causeOfType(failure, A2AAgentCardException.class))
                .hasMessageContaining("'applyFix'")
                .hasMessageEndingWith("within 1 s")
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void the_resolution_timeout_is_thirty_seconds_by_default_and_cannot_be_negative() {
        A2AClientBuilder<Fixer> builder = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class);

        assertThatThrownBy(() -> builder.resolutionTimeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
        assertThatThrownBy(
                        () -> AgenticServices.createAgenticSystem(FixAgentWithNegativeTimeout.class, (ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");

        // not set, on the builder or with the -1 that is the default of the annotation, it is 30 seconds
        assertThat(resolutionTimeoutOf(AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                        .resolutionTimeout(null)
                        .outputKey("fix")
                        .build()))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(resolutionTimeoutOf(AgenticServices.createAgenticSystem(FixAgent.class, (ChatModel) null)))
                .isEqualTo(Duration.ofSeconds(30));
        assertThat(resolutionTimeoutOf(AgenticServices.createAgenticSystem(ImpatientFixAgent.class, (ChatModel) null)))
                .isEqualTo(Duration.ofSeconds(1));
        // zero is kept as it is, and means no limit
        assertThat(resolutionTimeoutOf(AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                        .resolutionTimeout(Duration.ZERO)
                        .outputKey("fix")
                        .build()))
                .isEqualTo(Duration.ZERO);
        assertThat(resolutionTimeoutOf(
                        AgenticServices.createAgenticSystem(FixAgentWithoutTimeout.class, (ChatModel) null)))
                .isEqualTo(Duration.ZERO);
    }

    private static Duration resolutionTimeoutOf(Object a2aAgent) {
        return ((DefaultA2AClientBuilder<?>) Proxy.getInvocationHandler(a2aAgent)).resolutionTimeout;
    }

    @Test
    void a_zero_resolution_timeout_removes_the_limit() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.delayMillis = 300;
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .resolutionTimeout(Duration.ZERO)
                .outputKey("fix")
                .build();

        // no deadline at all, and not one that has already passed
        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");
        // without a deadline at which to abandon it, the fetch is made by the invocation itself
        assertThat(cardSource.fetchingThread).isSameAs(Thread.currentThread());
    }

    @Test
    void the_fetch_of_a_card_from_a_server_that_never_answers_is_aborted_at_the_timeout() throws Exception {
        // the real card resolver of the SDK, against a server that accepts the connection and never answers
        CountDownLatch connectionClosed = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            Thread silentServer = new Thread(() -> {
                try (Socket connection = server.accept()) {
                    InputStream request = connection.getInputStream();
                    while (request.read() != -1) {
                        // reads the request without ever answering it, until the client closes the connection
                    }
                } catch (IOException e) {
                    // a connection reset by the client is the end of the request too
                }
                connectionClosed.countDown();
            });
            silentServer.setDaemon(true);
            silentServer.start();
            Fixer fixer = AgenticServices.a2aBuilder("http://localhost:" + server.getLocalPort(), Fixer.class)
                    .resolutionTimeout(Duration.ofSeconds(2))
                    .outputKey("fix")
                    .build();

            assertThatThrownBy(() -> fixer.fix("a broken build"))
                    .isInstanceOf(A2AAgentCardException.class)
                    .hasMessageEndingWith("within 2 s")
                    .hasCauseInstanceOf(TimeoutException.class);

            // interrupting the fetch aborted the request: the server sees its connection closed
            assertThat(connectionClosed.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void name_and_id_are_known_without_fetching_the_card() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));

        // a typed agent is named after its agent method
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();
        AgentInstance fixerInstance = (AgentInstance) fixer;
        assertThat(fixerInstance.name()).isEqualTo("fix");
        assertThat(fixerInstance.agentId()).isEqualTo("fix");
        ((InternalAgent) fixer).appendId("$1");
        assertThat(fixerInstance.agentId()).isEqualTo("fix$1");
        assertThat(fixerInstance.name()).isEqualTo("fix");

        // an explicit name and description win
        Fixer namedFixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .name("fixer")
                .description("Applies a fix")
                .outputKey("fix")
                .build();
        assertThat(((AgentInstance) namedFixer).name()).isEqualTo("fixer");
        assertThat(((AgentInstance) namedFixer).description()).isEqualTo("Applies a fix");

        // the same holds for the declarative API
        NamedFixAgent namedDeclarative = AgenticServices.createAgenticSystem(NamedFixAgent.class, (ChatModel) null);
        assertThat(((AgentInstance) namedDeclarative).name()).isEqualTo("fixer");
        assertThat(((AgentInstance) namedDeclarative).description()).isEqualTo("Applies a fix to the analyzed problem");
        FixAgent declarative = AgenticServices.createAgenticSystem(FixAgent.class, (ChatModel) null);
        assertThat(((AgentInstance) declarative).name()).isEqualTo("applyFix");

        assertThat(cardSource.fetches).hasValue(0);
    }

    @Test
    void the_description_comes_from_the_card_and_the_name_does_not_change_after_the_fetch() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();
        AgentInstance fixerInstance = (AgentInstance) fixer;
        assertThat(cardSource.fetches).hasValue(0);

        assertThat(fixerInstance.description()).isEqualTo("Fixes the analyzed problem");
        assertThat(cardSource.fetches).hasValue(1);

        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");
        assertThat(fixerInstance.name()).isEqualTo("fix");
        assertThat(fixerInstance.agentId()).isEqualTo("fix");
        assertThat(((A2AClientInstance) fixer).agentCard().name()).isEqualTo("remote-fixer");
        assertThat(cardSource.fetches).hasValue(1);
    }

    @Test
    void untyped_agents_without_a_name_get_a_placeholder_name_derived_from_the_url() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));

        assertThat(untypedAgentName("http://fixers.example.com:8080")).isEqualTo("a2a-fixers.example.com-8080");
        assertThat(untypedAgentName("http://fixers.example.com:8080")).isEqualTo("a2a-fixers.example.com-8080");
        assertThat(untypedAgentName("http://fixers.example.com:8080/agents/fixer/"))
                .isEqualTo("a2a-fixers.example.com-8080-agents-fixer");
        assertThat(untypedAgentName("https://fixers.example.com")).isEqualTo("a2a-fixers.example.com");
        assertThat(cardSource.fetches).hasValue(0);
    }

    private static String untypedAgentName(String a2aServerUrl) {
        UntypedAgent agent = AgenticServices.a2aBuilder(a2aServerUrl)
                .inputKeys("analysis")
                .outputKey("fix")
                .build();
        return ((AgentInstance) agent).name();
    }

    @Test
    void the_name_and_description_of_the_agent_annotation_are_used_without_fetching_the_card() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));

        DescribedFixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", DescribedFixer.class)
                .outputKey("fix")
                .build();

        assertThat(((AgentInstance) fixer).name()).isEqualTo("fixer");
        assertThat(((AgentInstance) fixer).description()).isEqualTo("Applies a fix to the analyzed problem");
        assertThat(cardSource.fetches).hasValue(0);
    }

    @Test
    void a_failed_fetch_of_the_description_throws_the_card_exception_itself() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.failing = true;
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        assertThatThrownBy(() -> ((AgentInstance) fixer).description())
                .isExactlyInstanceOf(A2AAgentCardException.class)
                .hasCauseInstanceOf(A2AClientError.class);
    }

    @Test
    void a_missing_input_fails_before_the_card_is_fetched() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.failing = true;
        useLazyA2AService(cardSource, replying("fixed"));
        UntypedAgent fixer = AgenticServices.a2aBuilder("http://remote-host:8080")
                .inputKeys("analysis")
                .outputKey("fix")
                .build();

        assertThatThrownBy(() -> fixer.invoke(Map.of())).isInstanceOf(MissingArgumentException.class);
        assertThat(cardSource.fetches).hasValue(0);
    }

    @Test
    void the_message_of_the_card_exception_hides_credentials_of_the_url() {
        A2AAgentCardException exception = new A2AAgentCardException(
                "fixer", "https://user:secret@remote-host:8080/a2a?key=token", new IOException("connection refused"));

        assertThat(exception.getMessage())
                .contains("https://remote-host:8080/a2a")
                .doesNotContain("secret")
                .doesNotContain("token");
        assertThat(exception.a2aServerUrl()).isEqualTo("https://user:secret@remote-host:8080/a2a?key=token");

        A2AAgentCardException timeout = new A2AAgentCardException(
                "fixer",
                "https://user:secret@remote-host:8080/a2a?key=token",
                Duration.ofSeconds(1),
                new TimeoutException());

        assertThat(timeout.getMessage())
                .contains("https://remote-host:8080/a2a")
                .doesNotContain("secret")
                .doesNotContain("token");
    }

    @Test
    void the_tenant_auto_detected_from_the_card_is_applied_on_the_first_message() {
        AgentCard tenantCard = card(
                "remote-fixer",
                "Fixes the analyzed problem",
                new AgentInterface("JSONRPC", "http://localhost", "auto-tenant"));
        CardSource cardSource = new CardSource(tenantCard);
        ReplyingClient replyingClient = new ReplyingClient("fixed", tenantCard);
        useLazyA2AService(cardSource, card -> replyingClient.client);
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        // the tenant is not known before the card is fetched, and asking for it does not fetch the card
        assertThat(((A2AClientInstance) fixer).tenant()).isNull();
        assertThat(cardSource.fetches).hasValue(0);

        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");
        assertThat(replyingClient.sentParams).hasSize(1);
        assertThat(replyingClient.sentParams.get(0).tenant()).isEqualTo("auto-tenant");
        assertThat(((A2AClientInstance) fixer).tenant()).isEqualTo("auto-tenant");
        assertThat(cardSource.fetches).hasValue(1);
    }

    @Test
    @Timeout(60)
    void the_client_customizer_runs_once_when_the_agent_is_first_invoked() {
        AgentCard cardOfUnreachableAgent = card(
                "remote-fixer",
                "Fixes the analyzed problem",
                new AgentInterface("JSONRPC", UNREACHABLE_A2A_SERVER_URL, ""));
        CardSource cardSource = new CardSource(cardOfUnreachableAgent);
        // a real client is built from the card, so the customizer takes part in its creation
        useLazyA2AService(cardSource, null);
        AtomicInteger customizations = new AtomicInteger();
        Fixer fixer = AgenticServices.a2aBuilder(UNREACHABLE_A2A_SERVER_URL, Fixer.class)
                .clientCustomizer((ClientBuilder cb) -> {
                    customizations.incrementAndGet();
                    cb.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
                })
                .outputKey("fix")
                .build();
        assertThat(customizations).hasValue(0);

        // the remote agent does not answer the messages, but the card is fetched and the client created only once
        assertThatThrownBy(() -> fixer.fix("a broken build")).isNotInstanceOf(A2AAgentCardException.class);
        assertThatThrownBy(() -> fixer.fix("a broken build")).isNotInstanceOf(A2AAgentCardException.class);
        assertThat(cardSource.fetches).hasValue(1);
        assertThat(customizations).hasValue(1);
    }

    @Test
    void agentCard_fetches_the_card_before_the_first_invocation() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();

        // the warm-up for those who want to fail fast before the first invocation
        assertThat(((A2AClientInstance) fixer).agentCard().description()).isEqualTo("Fixes the analyzed problem");
        assertThat(cardSource.fetches).hasValue(1);

        assertThat(fixer.fix("a broken build")).isEqualTo("fixed");
        assertThat(cardSource.fetches).hasValue(1);
    }

    @Test
    void a_supervisor_fetches_the_card_of_a_subagent_without_description_when_first_invoked() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(fixer)
                .build();
        assertThat(cardSource.fetches).hasValue(0);

        assertThat(supervisor.invoke("fix the build")).isEqualTo("nothing to fix");
        assertThat(cardSource.fetches).hasValue(1);
        assertThat(systemMessage(plannerModel)).contains("'fix$0', 'Fixes the analyzed problem'");
    }

    @Test
    void a_supervisor_does_not_fetch_the_card_of_a_subagent_with_an_explicit_description() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .description("Applies a fix to the analyzed problem")
                .outputKey("fix")
                .build();
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(fixer)
                .build();

        assertThat(supervisor.invoke("fix the build")).isEqualTo("nothing to fix");
        assertThat(cardSource.fetches).hasValue(0);
        assertThat(systemMessage(plannerModel)).contains("'fix$0', 'Applies a fix to the analyzed problem'");
    }

    @Test
    void a_supervisor_leaves_out_an_unreachable_subagent_and_offers_it_again_once_it_is_back() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        cardSource.failing = true;
        useLazyA2AService(cardSource, replying("fixed"));
        Fixer fixer = AgenticServices.a2aBuilder("http://remote-host:8080", Fixer.class)
                .outputKey("fix")
                .build();
        AgenticScopeFunction<String> analyzer = AgenticServices.nonAiAgentBuilder(scope -> "analyzed")
                .name("analyzer")
                .description("Analyzes the problem")
                .outputKey("analysis")
                .build();
        ChatModelMock plannerModel = donePlannerModel();
        SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
                .chatModel(plannerModel)
                .responseStrategy(SupervisorResponseStrategy.SUMMARY)
                .subAgents(analyzer, fixer)
                .build();

        // the remote agent is down: the supervisor plans with its other subagents only, instead of failing
        assertThat(supervisor.invoke("fix the build")).isEqualTo("nothing to fix");
        assertThat(systemMessage(plannerModel, 0))
                .contains("'analyzer$0', 'Analyzes the problem'")
                .doesNotContain("fix$1");

        // the remote agent is back: the next invocation fetches its card and offers it to the model
        cardSource.failing = false;
        assertThat(supervisor.invoke("fix the build")).isEqualTo("nothing to fix");
        assertThat(plannerModel.requests()).hasSize(2);
        assertThat(systemMessage(plannerModel, 1)).contains("'fix$1', 'Fixes the analyzed problem'");
        assertThat(cardSource.fetches).hasValue(2);
    }

    /** An agent instance type the A2A client does not implement. */
    interface OtherAgentInstance extends AgentInstance {}

    @Test
    void the_a2a_agent_of_a_declarative_system_can_be_warmed_up_through_as() {
        CardSource cardSource = new CardSource(REMOTE_FIXER_CARD);
        useLazyA2AService(cardSource, replying("fixed"));
        Pipeline pipeline = AgenticServices.createAgenticSystem(Pipeline.class, (ChatModel) null);
        AgentInstance fixAgent = ((AgentInstance) pipeline)
                .subagents().stream()
                        .filter(subagent -> subagent.name().equals("applyFix"))
                        .findFirst()
                        .orElseThrow();
        assertThat(cardSource.fetches).hasValue(0);

        // the wrapper of the A2A agent gives access to the client, which fetches the card at once
        assertThat(fixAgent.as(A2AClientInstance.class).agentCard().name()).isEqualTo("remote-fixer");
        assertThat(cardSource.fetches).hasValue(1);
        assertThatThrownBy(() -> fixAgent.as(OtherAgentInstance.class)).isInstanceOf(ClassCastException.class);
    }

    /** The tokens the customizer below was invoked with. */
    static final List<String> CUSTOMIZER_TOKENS = new CopyOnWriteArrayList<>();

    public interface FixAgentWithResolvedCustomizerParameter {

        @A2AClientAgent(outputKey = "fix")
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return UNREACHABLE_A2A_SERVER_URL;
        }

        @A2AClientCustomizer
        static void customize(ClientBuilder cb, String token) {
            CUSTOMIZER_TOKENS.add(token);
            cb.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
        }
    }

    @Test
    @Timeout(60)
    void the_resolved_parameters_of_the_client_customizer_are_resolved_when_the_agent_is_built() {
        AgentCard cardOfUnreachableAgent = card(
                "remote-fixer",
                "Fixes the analyzed problem",
                new AgentInterface("JSONRPC", UNREACHABLE_A2A_SERVER_URL, ""));
        CardSource cardSource = new CardSource(cardOfUnreachableAgent);
        useLazyA2AService(cardSource, null);
        // a resolver providing a per-request value, like a token, to the String parameter of the customizer
        AtomicInteger resolutions = new AtomicInteger();
        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.supplierMethod().isAnnotationPresent(A2AClientCustomizer.class)
                        && context.parameter().getType() == String.class;
            }

            @Override
            public Object resolve(Context context) {
                return "token-" + resolutions.incrementAndGet();
            }
        });
        CUSTOMIZER_TOKENS.clear();
        try {
            FixAgentWithResolvedCustomizerParameter fixAgent = AgenticServices.createAgenticSystem(
                    FixAgentWithResolvedCustomizerParameter.class, (ChatModel) null);
            // the parameter is resolved on the thread building the agent, as for the other supplier methods
            assertThat(resolutions).hasValue(1);
            assertThat(CUSTOMIZER_TOKENS).isEmpty();

            // the customizer runs with that value when the client is created on the first invocation
            assertThatThrownBy(() -> fixAgent.applyFix("a broken build")).isNotInstanceOf(A2AAgentCardException.class);
            assertThat(CUSTOMIZER_TOKENS).containsExactly("token-1");
            assertThat(resolutions).hasValue(1);
        } finally {
            DeclarativeUtil.getSupplierParameterResolvers().clear();
        }
    }
}
