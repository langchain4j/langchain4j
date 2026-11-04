package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AClientCustomizer;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.declarative.SystemMessageProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.declarative.UserMessageProviderSupplier;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that the parameters of every kind of static helper method of a declarative agent can be provided by a
 * {@link SupplierParameterResolver}, mixed with the parameters provided by the agentic framework.
 */
class HelperMethodParameterResolverTest {

    /** Marks the parameters provided by the test resolver, as a dependency injection container would do. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Injected {}

    static final Map<Class<?>, Object> SERVICES = new HashMap<>();
    static final List<SupplierParameterResolver.Context> RESOLVED = new CopyOnWriteArrayList<>();

    @BeforeEach
    void registerResolver() {
        SERVICES.clear();
        RESOLVED.clear();
        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().isAnnotationPresent(Injected.class);
            }

            @Override
            public Object resolve(Context context) {
                RESOLVED.add(context);
                Object service = SERVICES.get(context.parameter().getType());
                if (service instanceof RuntimeException failure) {
                    throw failure;
                }
                return service;
            }
        });
    }

    @AfterEach
    void clearResolvers() {
        DeclarativeUtil.getSupplierParameterResolvers().clear();
    }

    static <T> T register(Class<T> type, T service) {
        SERVICES.put(type, service);
        return service;
    }

    // ── Services provided by the resolver ──

    record Threshold(int value) {}

    record Policy(String forbiddenName) {
        boolean allows(String name) {
            return !forbiddenName.equals(name);
        }
    }

    static class Formatter {
        String format(String text) {
            return text.toUpperCase();
        }
    }

    record Defaults(String topic) {}

    record Persona(String role) {}

    static class Console {
        String ask(String question) {
            return question + "? yes";
        }
    }

    static class PlannerFactory {
        int created;

        Planner create() {
            created++;
            return new SequentialPlanner();
        }
    }

    static class RecordingListener implements AgentListener {
        final List<String> invokedAgents = new CopyOnWriteArrayList<>();

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            invokedAgents.add(agentRequest.agentName());
        }
    }

    static class MemoryStore {
        final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

        ChatMemory memoryOf(Object memoryId) {
            return memories.computeIfAbsent(
                    memoryId,
                    id -> MessageWindowChatMemory.builder()
                            .id(id)
                            .maxMessages(10)
                            .build());
        }
    }

    record Endpoints(String a2aUrl) {}

    record ExchangeRates(double eurToUsd) {}

    /** Replies with the system message and the last user message it receives. */
    static ChatModelMock echoModel() {
        return new ChatModelMock(chatRequest -> {
            String system = chatRequest.messages().stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(message -> ((SystemMessage) message).text())
                    .findFirst()
                    .orElse("");
            ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
            String user = last instanceof UserMessage userMessage ? userMessage.singleText() : "";
            return AiMessage.from(system + " | " + user);
        });
    }

    /** Replies with the given responses in order. */
    static ChatModelMock scriptedModel(String... responses) {
        Iterator<String> iterator = List.of(responses).iterator();
        return new ChatModelMock(chatRequest -> AiMessage.from(iterator.next()));
    }

    static String receivedTexts(ChatModelMock model) {
        return model.requests().stream()
                .flatMap(request -> request.messages().stream())
                .map(message -> message instanceof SystemMessage systemMessage
                        ? systemMessage.text()
                        : message instanceof UserMessage userMessage ? userMessage.singleText() : "")
                .collect(Collectors.joining("\n"));
    }

    // ── @ExitCondition ──

    public static class Counter {

        @Agent(outputKey = "count")
        public static int increment(@V("count") int count) {
            return count + 1;
        }
    }

    public interface CountingLoop {

        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        int count(@V("count") int count);

        @ExitCondition(description = "the count reached the threshold")
        static boolean reached(@V("count") int count, @Injected Threshold threshold, @LoopCounter int loopCounter) {
            return count >= threshold.value() && loopCounter > 0;
        }
    }

    @Test
    void exit_condition_parameter_is_resolved() {
        register(Threshold.class, new Threshold(3));

        CountingLoop loop = AgenticServices.createAgenticSystem(CountingLoop.class);

        assertThat(loop.count(0)).isEqualTo(3);
        SupplierParameterResolver.Context context = RESOLVED.get(0);
        assertThat(context.declaringAgentClass()).isEqualTo(CountingLoop.class);
        assertThat(context.supplierMethod().getName()).isEqualTo("reached");
        assertThat(context.parameter().getType()).isEqualTo(Threshold.class);
    }

    // ── Non-AI agent, @ActivationCondition without value and @Output ──

    public static class GreetingAgent {

        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name, @Injected Formatter formatter) {
            return formatter.format("Hello " + name);
        }

        @ActivationCondition(description = "the name is allowed")
        public static boolean allowed(@V("name") String name, @Injected Policy policy) {
            return policy.allows(name);
        }
    }

    public interface GreetingSequence {

        @SequenceAgent(outputKey = "result", subAgents = GreetingAgent.class)
        String run(@V("name") String name);

        @Output
        static String output(AgenticScope agenticScope, @Injected Formatter formatter) {
            return formatter.format(agenticScope.readState("greeting", "no greeting"));
        }
    }

    @Test
    void non_ai_agent_activation_condition_and_output_parameters_are_resolved() {
        register(Formatter.class, new Formatter());
        register(Policy.class, new Policy("Voldemort"));

        GreetingSequence sequence = AgenticServices.createAgenticSystem(GreetingSequence.class);

        assertThat(sequence.run("Mario")).isEqualTo("HELLO MARIO");
        assertThat(sequence.run("Voldemort")).isEqualTo("NO GREETING");
    }

    public interface GreetingSequenceWithRecovery {

        @SequenceAgent(outputKey = "greeting", subAgents = GreetingAgent.class)
        String run(@V("name") String name);

        @ErrorHandler
        static ErrorRecoveryResult recover(ErrorContext errorContext) {
            return ErrorRecoveryResult.result("recovered from " + errorContext.exception().getMessage());
        }
    }

    @Test
    void failing_resolution_of_a_non_ai_agent_parameter_is_handled_by_the_error_handler() {
        SERVICES.put(Formatter.class, new IllegalStateException("formatter not available"));
        register(Policy.class, new Policy("Voldemort"));

        GreetingSequenceWithRecovery sequence =
                AgenticServices.createAgenticSystem(GreetingSequenceWithRecovery.class);

        assertThat(sequence.run("Mario")).isEqualTo("recovered from Failed to resolve the parameters of agent 'greet'");
    }

    @Test
    void resolved_parameters_are_not_arguments_of_a_non_ai_agent() {
        GreetingSequence sequence = AgenticServices.createAgenticSystem(GreetingSequence.class);

        AgentInstance greeter = ((AgentInstance) sequence).subagents().get(0);
        assertThat(greeter.arguments()).extracting(AgentArgument::name).containsExactly("name");
    }

    // ── Non-AI agent passed as an instance ──

    public static class InstanceGreeter {

        @Agent(outputKey = "greeting")
        public String greet(@V("name") String name, @Injected @V("formatter") Formatter formatter) {
            return formatter.format("Hello " + name);
        }
    }

    @Test
    void parameters_of_a_non_ai_agent_passed_as_instance_are_read_from_the_scope() {
        register(Formatter.class, new Formatter());
        Formatter scopeFormatter = new Formatter() {
            @Override
            String format(String text) {
                return text + "!";
            }
        };

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(new InstanceGreeter())
                .outputKey("greeting")
                .build();

        assertThat(sequence.invoke(Map.of("name", "Mario", "formatter", scopeFormatter)))
                .isEqualTo("Hello Mario!");
        assertThat(((AgentInstance) sequence).subagents().get(0).arguments())
                .extracting(AgentArgument::name)
                .containsExactly("name", "formatter");
        assertThat(RESOLVED).isEmpty();
    }

    // ── @ActivationCondition with value ──

    public static class Shouter {

        @Agent(outputKey = "greeting")
        public static String shout(@V("name") String name) {
            return name.toUpperCase() + "!";
        }
    }

    public interface ConditionalShout {

        @ConditionalAgent(outputKey = "greeting", subAgents = Shouter.class)
        String run(@V("name") String name);

        @ActivationCondition(value = Shouter.class, description = "the name is allowed")
        static boolean shout(@V("name") String name, @Injected Policy policy) {
            return policy.allows(name);
        }
    }

    @Test
    void conditional_activation_condition_parameter_is_resolved() {
        register(Policy.class, new Policy("Voldemort"));

        ConditionalShout conditional = AgenticServices.createAgenticSystem(ConditionalShout.class);

        assertThat(conditional.run("Mario")).isEqualTo("MARIO!");
        assertThat(conditional.run("Voldemort")).isNull();
    }

    // ── @BeforeCall and @ErrorHandler ──

    public static class TopicWriter {

        @Agent(outputKey = "story")
        public static String write(@V("topic") String topic) {
            return "A story about " + topic;
        }
    }

    public interface StoryWithDefaults {

        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("style") String style);

        @BeforeCall
        static void beforeCall(AgenticScope agenticScope, @Injected Defaults defaults) {
            agenticScope.writeStateIfAbsent("topic", defaults.topic());
        }
    }

    @Test
    void before_call_parameter_is_resolved() {
        register(Defaults.class, new Defaults("dragons"));

        StoryWithDefaults story = AgenticServices.createAgenticSystem(StoryWithDefaults.class);

        assertThat(story.write("comedy")).isEqualTo("A story about dragons");
    }

    public interface StoryWithRecovery {

        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("style") String style);

        @ErrorHandler
        static ErrorRecoveryResult recover(@Injected Defaults defaults, ErrorContext errorContext) {
            if (errorContext.exception() instanceof MissingArgumentException missing
                    && missing.argumentName().equals("topic")) {
                errorContext.agenticScope().writeState("topic", defaults.topic());
                return ErrorRecoveryResult.retry();
            }
            return ErrorRecoveryResult.throwException();
        }
    }

    @Test
    void error_handler_parameter_is_resolved() {
        register(Defaults.class, new Defaults("wizards"));

        StoryWithRecovery story = AgenticServices.createAgenticSystem(StoryWithRecovery.class);

        assertThat(story.write("comedy")).isEqualTo("A story about wizards");
    }

    public interface StoryWithInvalidErrorHandler {

        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("style") String style);

        @ErrorHandler
        static ErrorRecoveryResult recover(@Injected Defaults defaults) {
            return ErrorRecoveryResult.throwException();
        }
    }

    @Test
    void error_handler_without_error_context_is_rejected() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(StoryWithInvalidErrorHandler.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recover")
                .hasMessageContaining(ErrorContext.class.getName());
    }

    public interface StoryWithMistypedErrorHandler {

        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("style") String style);

        @ErrorHandler
        static ErrorRecoveryResult recover(@Injected Defaults defaults, String errorContext) {
            return ErrorRecoveryResult.throwException();
        }
    }

    @Test
    void error_handler_with_mistyped_error_context_is_rejected() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(StoryWithMistypedErrorHandler.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recover")
                .hasMessageContaining("argument 2 must be of type " + ErrorContext.class.getName());
    }

    public interface StoryWithErrorHandlerWithoutParameters {

        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("style") String style);

        @ErrorHandler
        static ErrorRecoveryResult recover() {
            return ErrorRecoveryResult.throwException();
        }
    }

    @Test
    void invalid_method_without_resolved_parameters_is_rejected_without_mentioning_resolvers() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(StoryWithErrorHandlerWithoutParameters.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have 1 arguments: [" + ErrorContext.class + "]")
                .hasMessageNotContaining("SupplierParameterResolver");
    }

    // ── @AgentListenerSupplier on a workflow agent ──

    public interface ListenedStory {

        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);

        @AgentListenerSupplier
        static AgentListener listener(@Injected RecordingListener listener) {
            return listener;
        }
    }

    @Test
    void workflow_listener_supplier_parameter_is_resolved() {
        RecordingListener listener = register(RecordingListener.class, new RecordingListener());

        ListenedStory story = AgenticServices.createAgenticSystem(ListenedStory.class);

        assertThat(story.write("dragons")).isEqualTo("A story about dragons");
        assertThat(listener.invokedAgents).contains("write");
    }

    // ── @PlannerSupplier ──

    public interface PlannedStory {

        @PlannerAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner(@Injected PlannerFactory factory) {
            return factory.create();
        }
    }

    @Test
    void planner_supplier_parameter_is_resolved_for_each_planner() {
        PlannerFactory factory = register(PlannerFactory.class, new PlannerFactory());

        PlannedStory story = AgenticServices.createAgenticSystem(PlannedStory.class);
        int createdAtBuild = factory.created;

        assertThat(story.write("dragons")).isEqualTo("A story about dragons");
        assertThat(story.write("wizards")).isEqualTo("A story about wizards");
        assertThat(factory.created).isEqualTo(createdAtBuild + 2);
    }

    // ── @HumanInTheLoop ──

    public interface Confirmation {

        @HumanInTheLoop(description = "Asks a confirmation", outputKey = "confirmation")
        static String ask(@V("name") String name, @Injected Console console) {
            return console.ask("Confirm " + name);
        }
    }

    public interface ConfirmationSequence {

        @SequenceAgent(outputKey = "confirmation", subAgents = Confirmation.class)
        String run(@V("name") String name);
    }

    @Test
    void human_in_the_loop_parameter_is_resolved_and_is_not_an_input() {
        register(Console.class, new Console());

        ConfirmationSequence sequence = AgenticServices.createAgenticSystem(ConfirmationSequence.class);

        assertThat(sequence.run("Mario")).isEqualTo("Confirm Mario? yes");
        AgentInstance humanInTheLoop = ((AgentInstance) sequence).subagents().get(0);
        assertThat(humanInTheLoop.arguments()).extracting(AgentArgument::name).containsExactly("name");
    }

    // ── AI agent: memory id based suppliers, and the chat model of the root agent ──

    public interface Assistant {

        @Agent(outputKey = "answer")
        String chat(@MemoryId String memoryId, @V("question") String question);

        @SystemMessageProviderSupplier
        static String systemMessage(Object memoryId, @Injected Persona persona) {
            return persona.role() + " of " + memoryId;
        }

        @UserMessageProviderSupplier
        static String userMessage(@Injected Persona persona, Object memoryId) {
            return "Answer as " + persona.role() + ": {{question}}";
        }

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId, @Injected MemoryStore store) {
            return store.memoryOf(memoryId);
        }
    }

    public interface AssistantSequence {

        @SequenceAgent(outputKey = "answer", subAgents = Assistant.class)
        String ask(@MemoryId String memoryId, @V("question") String question);

        @ChatModelSupplier
        static ChatModel chatModel(@Injected ChatModelMock model) {
            return model;
        }
    }

    @Test
    void memory_id_suppliers_and_root_chat_model_parameters_are_resolved() {
        register(Persona.class, new Persona("Butler"));
        MemoryStore store = register(MemoryStore.class, new MemoryStore());
        register(ChatModelMock.class, echoModel());

        AssistantSequence assistant = AgenticServices.createAgenticSystem(AssistantSequence.class);

        assertThat(assistant.ask("mario", "What time is it?"))
                .isEqualTo("Butler of mario | Answer as Butler: What time is it?");
        assertThat(store.memories).containsOnlyKeys("mario");
        assertThat(store.memories.get("mario").messages()).isNotEmpty();
    }

    // ── @A2AServerUrlSupplier ──

    public interface RemoteAgent {

        @A2AClientAgent(outputKey = "result")
        String invoke(@V("input") String input);

        @A2AServerUrlSupplier
        static String url(@Injected Endpoints endpoints) {
            return endpoints.a2aUrl();
        }
    }

    @Test
    void a2a_server_url_supplier_parameter_is_resolved() {
        register(Endpoints.class, new Endpoints("http://localhost:8080"));

        // The URL is resolved, then the A2A client cannot be built: langchain4j-agentic-a2a is not on the classpath
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(RemoteAgent.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No A2A service implementation found");

        assertThat(RESOLVED).extracting(c -> c.supplierMethod().getName()).containsExactly("url");
    }

    // ── @SupervisorRequest, and the agents described to the supervisor ──

    public static class Exchanger {

        @Agent(description = "Converts an amount of euros to dollars", outputKey = "dollars")
        public static double exchange(@V("euros") double euros, @Injected ExchangeRates rates) {
            return euros * rates.eurToUsd();
        }
    }

    public interface ExchangeSupervisor {

        @SupervisorAgent(
                outputKey = "result",
                subAgents = Exchanger.class,
                responseStrategy = SupervisorResponseStrategy.LAST)
        String invoke(@V("request") String request);

        @SupervisorRequest
        static String request(@V("request") String request, @Injected Persona persona) {
            return "As a " + persona.role() + ", " + request;
        }
    }

    @Test
    void supervisor_request_parameter_is_resolved_and_not_described_to_the_supervisor() {
        register(Persona.class, new Persona("banker"));
        register(ExchangeRates.class, new ExchangeRates(1.15));
        ChatModelMock plannerModel = scriptedModel("""
            {"agentName": "exchange", "arguments": {"euros": 100}}
            """, """
                    {"agentName": "done", "arguments": {"response": "100 EUR are 115 USD"}}
                    """);

        ExchangeSupervisor supervisor = AgenticServices.createAgenticSystem(ExchangeSupervisor.class, plannerModel);
        supervisor.invoke("convert 100 EUR to USD");

        String prompts = receivedTexts(plannerModel);
        assertThat(prompts)
                .contains("As a banker, convert 100 EUR to USD")
                .contains("[euros: double]")
                .doesNotContain("ExchangeRates")
                .doesNotContain("rates:");
        assertThat(RESOLVED).extracting(c -> c.supplierMethod().getName()).contains("request", "exchange");
    }

    // ── Supplier methods with a parameter not supported by any resolver ──

    public interface FormattingAssistant {

        @Agent(outputKey = "answer")
        String chat(@V("question") String question);

        @ToolsSupplier
        static Object tools(Formatter formatter) {
            return formatter;
        }
    }

    @Test
    void supplier_parameter_not_supported_by_any_resolver_fails_agent_creation() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(FormattingAssistant.class, echoModel()))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("No SupplierParameterResolver is registered for parameter")
                .hasMessageContaining(Formatter.class.getName())
                .hasMessageContaining("@ToolsSupplier method");
    }

    public interface PlannedStoryWithoutQualifier {

        @PlannerAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);

        @PlannerSupplier
        static Planner planner(PlannerFactory factory) {
            return factory.create();
        }
    }

    @Test
    void planner_supplier_parameter_not_supported_by_any_resolver_fails_agent_creation() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(PlannedStoryWithoutQualifier.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining(PlannerFactory.class.getName())
                .hasMessageContaining("@PlannerSupplier method");
    }

    @Test
    void supplier_parameter_fails_agent_creation_when_no_resolver_is_registered() {
        DeclarativeUtil.getSupplierParameterResolvers().clear();

        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(ListenedStory.class))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining(RecordingListener.class.getName())
                .hasMessageContaining("@AgentListenerSupplier method");
    }

    // ── Helper methods with a wrong signature are rejected, not ignored ──

    public interface RemoteAgentWithoutClientBuilder {

        @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "result")
        String invoke(@V("input") String input);

        @A2AClientCustomizer
        static void customize(@Injected Endpoints endpoints) {}
    }

    @Test
    void a2a_client_customizer_without_client_builder_is_rejected() {
        // rejected before the A2A client is created, so langchain4j-agentic-a2a is not needed
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(RemoteAgentWithoutClientBuilder.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customize")
                .hasMessageContaining("must have 1 arguments not provided by a SupplierParameterResolver");
    }

    public interface SupervisorWithoutMemoryId {

        @SupervisorAgent(outputKey = "result", subAgents = Exchanger.class)
        String invoke(@V("request") String request);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(@Injected MemoryStore store) {
            return store.memoryOf("default");
        }
    }

    @Test
    void supervisor_chat_memory_provider_without_memory_id_is_rejected() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(SupervisorWithoutMemoryId.class, echoModel()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatMemory")
                .hasMessageContaining("must have 1 arguments not provided by a SupplierParameterResolver");
    }

    // ── Without a supporting resolver ──

    public static class NamedGreetingAgent {

        @Agent(outputKey = "greeting")
        public static String greet(@V("name") String name, @Injected @V("formatter") Formatter formatter) {
            return formatter.format("Hello " + name);
        }
    }

    public interface NamedGreetingSequence {

        @SequenceAgent(outputKey = "greeting", subAgents = NamedGreetingAgent.class)
        String run(@V("name") String name);
    }

    @Test
    void parameter_not_supported_by_any_resolver_is_read_from_the_scope() {
        NamedGreetingSequence withResolver = AgenticServices.createAgenticSystem(NamedGreetingSequence.class);
        assertThat(((AgentInstance) withResolver).subagents().get(0).arguments())
                .extracting(AgentArgument::name)
                .containsExactly("name");

        DeclarativeUtil.getSupplierParameterResolvers().clear();

        NamedGreetingSequence withoutResolver = AgenticServices.createAgenticSystem(NamedGreetingSequence.class);
        assertThat(((AgentInstance) withoutResolver).subagents().get(0).arguments())
                .extracting(AgentArgument::name)
                .containsExactly("name", "formatter");
    }
}
