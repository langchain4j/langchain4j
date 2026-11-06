package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.BeforeCall;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.DecisionModelSupplier;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.declarative.LoopCounter;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.declarative.RegistryAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.declarative.SystemMessageProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.agentic.declarative.UserMessageProviderSupplier;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.internal.PlannerBasedInvocationHandler;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.agentic.workflow.impl.SequentialPlanner;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.ThresholdContext;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the helper objects of declarative agents: the objects whose instance methods, annotated with the declarative
 * annotations, configure an agent as the static methods of its type do. Every kind of helper is covered in its
 * instance form, mixing the parameters provided by the framework with one provided by a
 * {@link SupplierParameterResolver}, together with the conflict rules, the agents passed as instances and the
 * class-based agents built with an {@code agentInstanceFactory}, as an integration does.
 */
class HelperObjectsTest {

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
                return SERVICES.get(context.parameter().getType());
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

    static List<String> resolvedMethods() {
        return RESOLVED.stream()
                .map(context -> context.supplierMethod().getName())
                .toList();
    }

    /** The configurator of an integration providing the given helper objects to the agents of the given type only. */
    static AgentConfigurator rootHelpers(Class<?> rootAgentType, Object... helperObjects) {
        return new AgentConfigurator(
                ctx -> {}, null, null, null, type -> type == rootAgentType ? List.of(helperObjects) : List.of());
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

    record ExchangeRates(double eurToUsd) {}

    static class FormatterTool {
        private final Formatter formatter;

        FormatterTool(Formatter formatter) {
            this.formatter = formatter;
        }

        @Tool("Formats a text")
        String format(String text) {
            return formatter.format(text);
        }
    }

    static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

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
    }

    public static class LoopHelpers {

        @ExitCondition(description = "the count reached the threshold")
        boolean reached(@V("count") int count, @Injected Threshold threshold, @LoopCounter int loopCounter) {
            return count >= threshold.value() && loopCounter > 0;
        }
    }

    @Test
    void exit_condition_helper_is_invoked_on_the_helper_object() {
        register(Threshold.class, new Threshold(3));

        CountingLoop loop = AgenticServices.createAgenticSystem(
                CountingLoop.class, rootHelpers(CountingLoop.class, new LoopHelpers()));

        assertThat(loop.count(0)).isEqualTo(3);
        SupplierParameterResolver.Context context = RESOLVED.get(0);
        assertThat(context.declaringAgentClass()).isEqualTo(CountingLoop.class);
        assertThat(context.supplierMethod().getDeclaringClass()).isEqualTo(LoopHelpers.class);
        assertThat(context.parameter().getType()).isEqualTo(Threshold.class);
    }

    /**
     * Shaped like the proxy generated by a container: it overrides the helper method without its annotation and
     * delegates to the actual instance.
     */
    public static class LoopHelpersProxy extends LoopHelpers {
        private final LoopHelpers delegate;
        final AtomicInteger invocations = new AtomicInteger();

        LoopHelpersProxy(LoopHelpers delegate) {
            this.delegate = delegate;
        }

        @Override
        boolean reached(int count, Threshold threshold, int loopCounter) {
            invocations.incrementAndGet();
            return delegate.reached(count, threshold, loopCounter);
        }
    }

    @Test
    void helper_method_overridden_without_its_annotation_is_invoked_through_the_override() {
        register(Threshold.class, new Threshold(3));
        LoopHelpersProxy proxy = new LoopHelpersProxy(new LoopHelpers());

        CountingLoop loop =
                AgenticServices.createAgenticSystem(CountingLoop.class, rootHelpers(CountingLoop.class, proxy));

        assertThat(loop.count(0)).isEqualTo(3);
        assertThat(proxy.invocations).hasPositiveValue();
        assertThat(RESOLVED.get(0).supplierMethod().getDeclaringClass()).isEqualTo(LoopHelpers.class);
    }

    static final List<RecordingListener> LOOP_LISTENERS = new CopyOnWriteArrayList<>();

    public interface ListenedCountingLoop {

        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        int count(@V("count") int count);

        @AgentListenerSupplier
        static AgentListener listener() {
            RecordingListener listener = new RecordingListener();
            LOOP_LISTENERS.add(listener);
            return listener;
        }
    }

    @Test
    void static_helpers_are_applied_once_next_to_the_instance_helpers() {
        register(Threshold.class, new Threshold(3));
        LOOP_LISTENERS.clear();

        ListenedCountingLoop loop = AgenticServices.createAgenticSystem(
                ListenedCountingLoop.class, rootHelpers(ListenedCountingLoop.class, new LoopHelpers()));

        assertThat(loop.count(0)).isEqualTo(3);
        assertThat(LOOP_LISTENERS).singleElement().satisfies(listener -> assertThat(listener.invokedAgents)
                .containsExactly("count"));
    }

    // ── @BeforeCall, @Output and @ErrorHandler ──

    public static class TopicWriter {

        @Agent(outputKey = "story")
        public static String write(@V("topic") String topic) {
            return "A story about " + topic;
        }
    }

    public interface Story {

        @SequenceAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("style") String style);
    }

    public static class StoryHelpers {

        @BeforeCall
        void defaultTopic(AgenticScope agenticScope, @Injected Defaults defaults) {
            agenticScope.writeStateIfAbsent("topic", defaults.topic());
        }

        @Output
        String output(@V("story") String story, @Injected Formatter formatter) {
            return formatter.format(story);
        }
    }

    @Test
    void before_call_and_output_helpers_are_invoked_on_the_helper_object() {
        register(Defaults.class, new Defaults("dragons"));
        register(Formatter.class, new Formatter());

        Story story = AgenticServices.createAgenticSystem(Story.class, rootHelpers(Story.class, new StoryHelpers()));

        assertThat(story.write("comedy")).isEqualTo("A STORY ABOUT DRAGONS");
        assertThat(resolvedMethods()).containsExactly("defaultTopic", "output");
    }

    public static class RecoveringHelpers {

        @ErrorHandler
        ErrorRecoveryResult recover(@Injected Defaults defaults, ErrorContext errorContext) {
            if (errorContext.exception() instanceof MissingArgumentException missing
                    && missing.argumentName().equals("topic")) {
                errorContext.agenticScope().writeState("topic", defaults.topic());
                return ErrorRecoveryResult.retry();
            }
            return ErrorRecoveryResult.throwException();
        }
    }

    @Test
    void error_handler_helper_is_invoked_on_the_helper_object() {
        register(Defaults.class, new Defaults("wizards"));

        Story story =
                AgenticServices.createAgenticSystem(Story.class, rootHelpers(Story.class, new RecoveringHelpers()));

        assertThat(story.write("comedy")).isEqualTo("A story about wizards");
    }

    // ── @ActivationCondition, with and without value ──

    public static class Shouter {

        @Agent(outputKey = "greeting")
        public static String shout(@V("name") String name) {
            return name.toUpperCase() + "!";
        }
    }

    public static class Whisperer {

        @Agent(outputKey = "greeting")
        public static String whisper(@V("name") String name) {
            return name.toLowerCase() + "...";
        }
    }

    public interface ConditionalGreeting {

        @ConditionalAgent(
                outputKey = "greeting",
                subAgents = {Shouter.class, Whisperer.class})
        String greet(@V("name") String name, @V("loud") boolean loud);
    }

    public static class GreetingConditions {

        @ActivationCondition(value = Shouter.class, description = "loud and allowed")
        boolean shout(@V("loud") boolean loud, @V("name") String name, @Injected Policy policy) {
            return loud && policy.allows(name);
        }

        @ActivationCondition(value = Whisperer.class, description = "quiet")
        boolean whisper(@V("loud") boolean loud) {
            return !loud;
        }
    }

    @Test
    void several_conditional_activation_conditions_can_be_instance_methods_of_one_helper_object() {
        register(Policy.class, new Policy("Voldemort"));

        ConditionalGreeting greeting = AgenticServices.createAgenticSystem(
                ConditionalGreeting.class, rootHelpers(ConditionalGreeting.class, new GreetingConditions()));

        assertThat(greeting.greet("Mario", true)).isEqualTo("MARIO!");
        assertThat(greeting.greet("Mario", false)).isEqualTo("mario...");
        assertThat(greeting.greet("Voldemort", true)).isNull();
    }

    public interface GreetingSequence {

        @SequenceAgent(outputKey = "greeting", subAgents = Shouter.class)
        String run(@V("name") String name);
    }

    public static class GreetingGate {

        @ActivationCondition(description = "the name is allowed")
        boolean allowed(@V("name") String name, @Injected Policy policy) {
            return policy.allows(name);
        }
    }

    public interface Greetings {

        @SequenceAgent(outputKey = "greeting", subAgents = GreetingSequence.class)
        String run(@V("name") String name);
    }

    @Test
    void helper_objects_resolver_provides_the_helpers_of_the_nested_agents() {
        register(Policy.class, new Policy("Voldemort"));
        var configurator = new AgentConfigurator(
                ctx -> {},
                null,
                null,
                null,
                type -> type == GreetingSequence.class ? List.of(new GreetingGate()) : null);

        Greetings greetings = AgenticServices.createAgenticSystem(Greetings.class, configurator);

        assertThat(greetings.run("Mario")).isEqualTo("MARIO!");
        assertThat(greetings.run("Voldemort")).isNull();
    }

    // ── @ParallelExecutor ──

    public static class Left {

        @Agent(outputKey = "left")
        public static String left(@V("text") String text) {
            return Thread.currentThread().getName();
        }
    }

    public static class Right {

        @Agent(outputKey = "right")
        public static String right(@V("text") String text) {
            return Thread.currentThread().getName();
        }
    }

    public interface BothSides {

        @ParallelAgent(
                outputKey = "sides",
                subAgents = {Left.class, Right.class})
        List<String> run(@V("text") String text);
    }

    public static class ExecutorHelpers {
        ExecutorService created;

        @ParallelExecutor
        Executor executor(@Injected ThreadFactory threadFactory) {
            created = Executors.newFixedThreadPool(2, threadFactory);
            return created;
        }
    }

    public static class SidesOutput {

        @Output
        List<String> sides(@V("left") String left, @V("right") String right) {
            return List.of(left, right);
        }
    }

    @Test
    void parallel_executor_helper_is_invoked_on_the_helper_object() {
        register(ThreadFactory.class, namedThreads("helper-"));

        ExecutorHelpers executorHelpers = new ExecutorHelpers();

        // two helper objects providing different helpers
        BothSides bothSides = AgenticServices.createAgenticSystem(
                BothSides.class, rootHelpers(BothSides.class, executorHelpers, new SidesOutput()));

        assertThat(bothSides.run("x"))
                .hasSize(2)
                .allSatisfy(thread -> assertThat(thread).startsWith("helper-"));
        executorHelpers.created.shutdown();
    }

    // ── AI agent suppliers ──

    public interface Assistant {

        @Agent(outputKey = "answer")
        String chat(@MemoryId String memoryId, @V("question") String question);
    }

    public static class AssistantHelpers {
        final MemoryStore store = new MemoryStore();

        @ChatModelSupplier
        ChatModel chatModel(@Injected ChatModelMock model) {
            return model;
        }

        @SystemMessageProviderSupplier
        String systemMessage(Object memoryId, @Injected Persona persona) {
            return persona.role() + " of " + memoryId;
        }

        @UserMessageProviderSupplier
        String userMessage(@Injected Persona persona, Object memoryId) {
            return "Answer as " + persona.role() + ": {{question}}";
        }

        @ChatMemoryProviderSupplier
        ChatMemory chatMemory(Object memoryId) {
            return store.memoryOf(memoryId);
        }

        @AgentListenerSupplier
        AgentListener listener(@Injected RecordingListener listener) {
            return listener;
        }

        @ToolsSupplier
        Object tools(@Injected Formatter formatter) {
            return new FormatterTool(formatter);
        }
    }

    public interface AssistantSequence {

        @SequenceAgent(outputKey = "answer", subAgents = Assistant.class)
        String ask(@MemoryId String memoryId, @V("question") String question);
    }

    @Test
    void ai_agent_suppliers_are_invoked_on_the_helper_object() {
        register(Persona.class, new Persona("Butler"));
        register(Formatter.class, new Formatter());
        ChatModelMock model = register(ChatModelMock.class, echoModel());
        RecordingListener listener = register(RecordingListener.class, new RecordingListener());
        AssistantHelpers helpers = new AssistantHelpers();
        var configurator = new AgentConfigurator(
                ctx -> {}, null, null, null, type -> type == Assistant.class ? List.of(helpers) : List.of());

        AssistantSequence assistant = AgenticServices.createAgenticSystem(AssistantSequence.class, configurator);

        assertThat(assistant.ask("mario", "What time is it?"))
                .isEqualTo("Butler of mario | Answer as Butler: What time is it?");
        assertThat(helpers.store.memories).containsOnlyKeys("mario");
        assertThat(helpers.store.memories.get("mario").messages()).isNotEmpty();
        assertThat(listener.invokedAgents).contains("chat");
        assertThat(model.request().toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("format");
    }

    public interface Translator {

        @dev.langchain4j.service.UserMessage("Translate {{text}} to {{language}}")
        @Agent(outputKey = "translation")
        String translate(@V("text") String text, @V("language") String language);
    }

    public static class TranslatorHelpers {
        final ChatModelMock english = ChatModelMock.thatAlwaysResponds("hello");
        final ChatModelMock italian = ChatModelMock.thatAlwaysResponds("ciao");

        @ChatModelSupplier
        ChatModel chatModel(@V("language") String language, @Injected Formatter formatter) {
            return formatter.format(language).equals("ITALIAN") ? italian : english;
        }
    }

    public interface Translation {

        @SequenceAgent(outputKey = "translation", subAgents = Translator.class)
        String translate(@V("text") String text, @V("language") String language);
    }

    @Test
    void dynamic_chat_model_supplier_helper_is_invoked_on_the_helper_object_at_each_invocation() {
        register(Formatter.class, new Formatter());
        TranslatorHelpers helpers = new TranslatorHelpers();
        var configurator = new AgentConfigurator(
                ctx -> {}, null, null, null, type -> type == Translator.class ? List.of(helpers) : List.of());

        Translation translation = AgenticServices.createAgenticSystem(Translation.class, configurator);

        assertThat(translation.translate("hi", "italian")).isEqualTo("ciao");
        assertThat(translation.translate("hi", "english")).isEqualTo("hello");
        assertThat(helpers.italian.requests()).hasSize(1);
        assertThat(helpers.english.requests()).hasSize(1);
    }

    // ── @PlannerSupplier ──

    public interface PlannedStory {

        @PlannerAgent(outputKey = "story", subAgents = TopicWriter.class)
        String write(@V("topic") String topic);
    }

    public static class PlannerHelpers {

        @PlannerSupplier
        Planner planner(@Injected PlannerFactory factory) {
            return factory.create();
        }
    }

    @Test
    void planner_supplier_helper_is_invoked_on_the_helper_object_for_each_planner() {
        PlannerFactory factory = register(PlannerFactory.class, new PlannerFactory());

        PlannedStory story = AgenticServices.createAgenticSystem(
                PlannedStory.class, rootHelpers(PlannedStory.class, new PlannerHelpers()));
        int createdAtBuild = factory.created;

        assertThat(story.write("dragons")).isEqualTo("A story about dragons");
        assertThat(story.write("wizards")).isEqualTo("A story about wizards");
        assertThat(factory.created).isEqualTo(createdAtBuild + 2);
    }

    // ── Supervisor ──

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
    }

    public static class SupervisorHelpers {
        final MemoryStore store = new MemoryStore();
        final ChatModelMock plannerModel;

        SupervisorHelpers(ChatModelMock plannerModel) {
            this.plannerModel = plannerModel;
        }

        @SupervisorRequest
        String request(@V("request") String request, @Injected Persona persona) {
            return "As a " + persona.role() + ", " + request;
        }

        @ChatModelSupplier
        ChatModel chatModel() {
            return plannerModel;
        }

        @ChatMemoryProviderSupplier
        ChatMemory chatMemory(Object memoryId) {
            return store.memoryOf(memoryId);
        }

        @Output
        String output(@V("dollars") double dollars, @Injected Formatter formatter) {
            return formatter.format(dollars + " dollars");
        }
    }

    static ChatModelMock exchangePlannerModel() {
        return scriptedModel("""
            {"agentName": "exchange", "arguments": {"euros": 100}}
            """, """
            {"agentName": "done", "arguments": {"response": "100 EUR are 115 USD"}}
            """);
    }

    @Test
    void supervisor_helpers_are_invoked_on_the_helper_object() {
        register(Persona.class, new Persona("banker"));
        register(ExchangeRates.class, new ExchangeRates(1.5));
        register(Formatter.class, new Formatter());
        SupervisorHelpers helpers = new SupervisorHelpers(exchangePlannerModel());

        ExchangeSupervisor supervisor = AgenticServices.createAgenticSystem(
                ExchangeSupervisor.class, rootHelpers(ExchangeSupervisor.class, helpers));

        assertThat(supervisor.invoke("convert 100 EUR to USD")).isEqualTo("150.0 DOLLARS");
        assertThat(receivedTexts(helpers.plannerModel)).contains("As a banker, convert 100 EUR to USD");
        assertThat(helpers.store.memories).hasSize(1);
    }

    // ── Decision agent suppliers ──

    public interface TransactionGate {

        @Decide("Does this money transfer look suspicious?")
        @Agent(value = "Checks whether a money transfer looks suspicious", outputKey = "suspicious")
        boolean check(@V("transfer") String transfer);
    }

    public static class GateHelpers {
        final List<ThresholdContext> contexts = new ArrayList<>();

        @DecisionModelSupplier
        DecisionModel decisionModel(@Injected DecisionModelMock model) {
            return model;
        }

        @DecisionThresholdSupplier
        double threshold(ThresholdContext context, @Injected Threshold threshold) {
            contexts.add(context);
            return threshold.value() / 100.0;
        }
    }

    @Test
    void decision_suppliers_are_invoked_on_the_helper_object() {
        register(DecisionModelMock.class, DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.7));
        register(Threshold.class, new Threshold(80));
        GateHelpers helpers = new GateHelpers();

        TransactionGate gate =
                AgenticServices.createAgenticSystem(TransactionGate.class, rootHelpers(TransactionGate.class, helpers));

        // the probability of "yes" is 0.7, below the threshold of 0.8
        assertThat(gate.check("10 USD to the baker")).isFalse();
        assertThat(helpers.contexts).hasSize(1);
    }

    // ── @HumanInTheLoop on an instance ──

    public static class Confirmation {

        @HumanInTheLoop(description = "Asks a confirmation", outputKey = "confirmation")
        public String ask(@V("name") String name, @Injected Console console) {
            return console.ask("Confirm " + name);
        }

        @ActivationCondition(description = "the name is allowed")
        boolean allowed(@V("name") String name, @Injected Policy policy) {
            return policy.allows(name);
        }
    }

    @Test
    void human_in_the_loop_object_is_a_subagent_and_its_own_helper_object() {
        register(Console.class, new Console());
        register(Policy.class, new Policy("Voldemort"));

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(new Confirmation())
                .outputKey("confirmation")
                .build();

        assertThat(sequence.invoke(Map.of("name", "Mario"))).isEqualTo("Confirm Mario? yes");
        assertThat(sequence.invoke(Map.of("name", "Voldemort"))).isNull();
        AgentInstance confirmation = ((AgentInstance) sequence).subagents().get(0);
        assertThat(confirmation.arguments()).extracting(AgentArgument::name).containsExactly("name");
        assertThat(confirmation.activationCondition()).isNotNull();
    }

    // ── Non-AI agent on an instance, its parameters resolved and its activation condition on the instance ──

    public static class InstanceGreeter {
        int calls;

        @Agent(outputKey = "greeting")
        public String greet(@V("name") String name, @Injected Formatter formatter) {
            calls++;
            return formatter.format("Hello " + name);
        }

        @ActivationCondition(description = "the name is allowed")
        boolean allowed(@V("name") String name, @Injected Policy policy) {
            return policy.allows(name);
        }
    }

    @Test
    void instance_agent_passed_as_object_gets_resolved_parameters_and_its_instance_activation_condition() {
        register(Formatter.class, new Formatter());
        register(Policy.class, new Policy("Voldemort"));
        InstanceGreeter greeter = new InstanceGreeter();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(greeter)
                .outputKey("greeting")
                .build();

        assertThat(sequence.invoke(Map.of("name", "Mario"))).isEqualTo("HELLO MARIO");
        assertThat(sequence.invoke(Map.of("name", "Voldemort"))).isNull();
        assertThat(greeter.calls).isEqualTo(1);
        AgentInstance greeterAgent = ((AgentInstance) sequence).subagents().get(0);
        assertThat(greeterAgent.arguments()).extracting(AgentArgument::name).containsExactly("name");
        assertThat(greeterAgent.topology()).isEqualTo(AgenticSystemTopology.NON_AI_AGENT);
        assertThat(resolvedMethods()).contains("greet", "allowed");
    }

    // ── Class-based workflow agents with an agentInstanceFactory: the contract an integration relies on ──

    /**
     * The shape of the object an integration generates for a class-based agent: a subclass of the agent class whose
     * agent method delegates to the invocation handler, and which implements {@link InternalAgent} and
     * {@link AgenticScopeOwner} by delegating to the handler too.
     */
    interface GeneratedAgent extends InternalAgent, AgenticScopeOwner {

        PlannerBasedInvocationHandler handler();

        default Object invokeHandler(
                Class<?> declaringClass, String methodName, Class<?>[] parameterTypes, Object... args) {
            try {
                Method method = declaringClass.getMethod(methodName, parameterTypes);
                return handler().invoke(this, method, args);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        default AgenticScopeOwner withAgenticScope(DefaultAgenticScope agenticScope) {
            return (AgenticScopeOwner) invokeHandler(
                    AgenticScopeOwner.class,
                    "withAgenticScope",
                    new Class<?>[] {DefaultAgenticScope.class},
                    agenticScope);
        }

        @Override
        default AgenticScopeRegistry registry() {
            return (AgenticScopeRegistry) invokeHandler(AgenticScopeOwner.class, "registry", new Class<?>[0]);
        }

        @Override
        default Class<?> type() {
            return handler().type();
        }

        @Override
        default Class<? extends Planner> plannerType() {
            return handler().plannerType();
        }

        @Override
        default String name() {
            return handler().name();
        }

        @Override
        default String agentId() {
            return handler().agentId();
        }

        @Override
        default String description() {
            return handler().description();
        }

        @Override
        default Type outputType() {
            return handler().outputType();
        }

        @Override
        default String outputKey() {
            return handler().outputKey();
        }

        @Override
        default boolean async() {
            return handler().async();
        }

        @Override
        default AgentCondition activationCondition() {
            return handler().activationCondition();
        }

        @Override
        default List<AgentArgument> arguments() {
            return handler().arguments();
        }

        @Override
        default AgentInstance parent() {
            return handler().parent();
        }

        @Override
        default List<AgentInstance> subagents() {
            return handler().subagents();
        }

        @Override
        default AgenticSystemTopology topology() {
            return handler().topology();
        }

        @Override
        default <A extends AgentInstance> A as(Class<A> agentInstanceClass) {
            return handler().as(agentInstanceClass);
        }

        @Override
        default void setParent(InternalAgent parent) {
            handler().setParent(parent);
        }

        @Override
        default void registerInheritedParentListener(AgentListener parentListener) {
            handler().registerInheritedParentListener(parentListener);
        }

        @Override
        default void appendId(String idSuffix) {
            handler().appendId(idSuffix);
        }

        @Override
        default AgentListener listener() {
            return handler().listener();
        }

        @Override
        default boolean allowStreamingOutput() {
            return handler().allowStreamingOutput();
        }

        @Override
        default boolean allowChatMemory() {
            return handler().allowChatMemory();
        }

        @Override
        default boolean compensateOnError() {
            return handler().compensateOnError();
        }

        @Override
        default void enableCrossAgentCompensation() {
            handler().enableCrossAgentCompensation();
        }
    }

    public static class Polisher {

        @Agent(outputKey = "story")
        public static String polish(@V("story") String story) {
            return story + "!";
        }
    }

    /** A class-based loop agent: the agent method is {@code native}, the exit condition is an instance method. */
    public static class StyleLoop {

        @LoopAgent(outputKey = "story", maxIterations = 5, subAgents = Polisher.class)
        public native String polish(@V("story") String story);

        @ExitCondition(description = "polished enough")
        boolean polished(@V("story") String story, @LoopCounter int loopCounter, @Injected Threshold threshold) {
            return story.length() >= threshold.value();
        }
    }

    static class StyleLoopImpl extends StyleLoop implements GeneratedAgent {
        private final PlannerBasedInvocationHandler handler;

        StyleLoopImpl(PlannerBasedInvocationHandler handler) {
            this.handler = handler;
        }

        @Override
        public PlannerBasedInvocationHandler handler() {
            return handler;
        }

        @Override
        public String polish(String story) {
            return (String) invokeHandler(StyleLoop.class, "polish", new Class<?>[] {String.class}, story);
        }
    }

    /** A class-based sequence agent nesting the class-based loop, with an instance output. */
    public static class StoryPipeline {

        @SequenceAgent(
                outputKey = "story",
                subAgents = {TopicWriter.class, StyleLoop.class})
        public native String write(@V("topic") String topic);

        @Output
        String output(@V("story") String story, @Injected Formatter formatter) {
            return formatter.format(story);
        }
    }

    static class StoryPipelineImpl extends StoryPipeline implements GeneratedAgent {
        private final PlannerBasedInvocationHandler handler;

        StoryPipelineImpl(PlannerBasedInvocationHandler handler) {
            this.handler = handler;
        }

        @Override
        public PlannerBasedInvocationHandler handler() {
            return handler;
        }

        @Override
        public String write(String topic) {
            return (String) invokeHandler(StoryPipeline.class, "write", new Class<?>[] {String.class}, topic);
        }
    }

    /** Generates the agent objects as an integration does: a subclass for a class, a proxy for an interface. */
    static final Function<InternalAgent, Object> AGENT_INSTANCE_FACTORY = internalAgent -> {
        Class<?> type = internalAgent.type();
        if (type == StyleLoop.class) {
            return new StyleLoopImpl((PlannerBasedInvocationHandler) internalAgent);
        }
        if (type == StoryPipeline.class) {
            return new StoryPipelineImpl((PlannerBasedInvocationHandler) internalAgent);
        }
        return Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type, InternalAgent.class, AgenticScopeOwner.class},
                (InvocationHandler) internalAgent);
    };

    /** The helper object of a class-based agent is an instance of the agent class, as a container bean would be. */
    static final Map<Class<?>, Object> BEANS = Map.of(
            StyleLoop.class, new StyleLoop(),
            StoryPipeline.class, new StoryPipeline());

    @Test
    void class_based_sequence_and_loop_agents_work_with_an_agent_instance_factory_and_their_own_helpers() {
        register(Threshold.class, new Threshold(22));
        register(Formatter.class, new Formatter());
        var configurator = new AgentConfigurator(
                ctx -> {},
                null,
                AGENT_INSTANCE_FACTORY,
                null,
                type -> BEANS.containsKey(type) ? List.of(BEANS.get(type)) : List.of());

        StoryPipeline pipeline = AgenticServices.createAgenticSystem(StoryPipeline.class, configurator);

        // "A story about dragons" is 21 characters long: one polishing round reaches the threshold
        assertThat(pipeline).isInstanceOf(StoryPipelineImpl.class);
        assertThat(pipeline.write("dragons")).isEqualTo("A STORY ABOUT DRAGONS!");
        assertThat(((AgentInstance) pipeline).subagents())
                .extracting(AgentInstance::name)
                .containsExactly("write", "polish");
        assertThat(resolvedMethods()).contains("polished", "output");
    }

    // ── The agent kinds whose helpers used to be static only: the resolver reaches them too ──

    public static class SignRequest {

        @HumanInTheLoop(description = "Asks a signature", outputKey = "signature")
        public static String sign(@V("name") String name) {
            return name + " signed";
        }
    }

    public interface Signing {

        @SequenceAgent(outputKey = "signature", subAgents = SignRequest.class)
        String run(@V("name") String name);
    }

    @Test
    void declarative_human_in_the_loop_class_gets_the_activation_condition_of_its_helper_object() {
        register(Policy.class, new Policy("Voldemort"));

        Signing signing =
                AgenticServices.createAgenticSystem(Signing.class, rootHelpers(SignRequest.class, new GreetingGate()));

        assertThat(signing.run("Mario")).isEqualTo("Mario signed");
        assertThat(signing.run("Voldemort")).isNull();
        assertThat(resolvedMethods()).contains("allowed");
    }

    public interface Shouting {

        @SequenceAgent(outputKey = "greeting", subAgents = Shouter.class)
        String run(@V("name") String name);
    }

    @Test
    void non_ai_agent_class_with_a_static_agent_method_gets_the_activation_condition_of_its_helper_object() {
        register(Policy.class, new Policy("Voldemort"));

        Shouting shouting =
                AgenticServices.createAgenticSystem(Shouting.class, rootHelpers(Shouter.class, new GreetingGate()));

        assertThat(shouting.run("Mario")).isEqualTo("MARIO!");
        assertThat(shouting.run("Voldemort")).isNull();
        assertThat(resolvedMethods()).contains("allowed");
    }

    public interface RegistryGreeter {

        @RegistryAgent("greeter")
        String greet(@V("name") String name);
    }

    public interface RegistryGreeting {

        @SequenceAgent(outputKey = "greeting", subAgents = RegistryGreeter.class)
        String run(@V("name") String name);
    }

    @Test
    void registry_agent_rejects_the_activation_condition_of_its_helper_object() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(
                        RegistryGreeting.class, rootHelpers(RegistryGreeter.class, new GreetingGate())))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("not supported on the @RegistryAgent")
                .hasMessageContaining("'greeter'");
    }

    public interface Rephraser {

        @dev.langchain4j.service.UserMessage("Rephrase {{text}}")
        @Agent(outputKey = "rephrased")
        String translate(@V("text") String text);
    }

    public interface Rephrasing {

        @SequenceAgent(outputKey = "rephrased", subAgents = Rephraser.class)
        String run(@V("text") String text);
    }

    public static class RephrasingModel {
        final ChatModelMock model = scriptedModel("ciao");

        @ChatModelSupplier
        ChatModel chatModel(@Injected Persona persona) {
            return model;
        }
    }

    @Test
    void default_chat_model_of_the_system_comes_from_the_helper_object_of_the_root_agent() {
        register(Persona.class, new Persona("translator"));
        RephrasingModel rephrasingModel = new RephrasingModel();
        AtomicInteger rootResolutions = new AtomicInteger();
        var configurator = new AgentConfigurator(ctx -> {}, null, null, null, type -> {
            if (type != Rephrasing.class) {
                return List.of();
            }
            rootResolutions.incrementAndGet();
            return List.of(rephrasingModel);
        });

        Rephrasing rephrasing = AgenticServices.createAgenticSystem(Rephrasing.class, configurator);

        assertThat(rephrasing.run("hello")).isEqualTo("ciao");
        assertThat(rephrasingModel.model.requests()).hasSize(1);
        // the helpers of the root agent are read twice, for its default chat model and to build it, resolved once
        assertThat(rootResolutions).hasValue(1);
    }

    // ── Conflicts ──

    public interface CountingLoopWithStaticExit {

        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        int count(@V("count") int count);

        @ExitCondition
        static boolean done(@V("count") int count) {
            return count >= 5;
        }
    }

    @Test
    void static_and_instance_helpers_for_the_same_annotation_are_rejected() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(
                        CountingLoopWithStaticExit.class,
                        rootHelpers(CountingLoopWithStaticExit.class, new LoopHelpers())))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("@ExitCondition")
                .hasMessageContaining("done")
                .hasMessageContaining("reached")
                .hasMessageContaining(LoopHelpers.class.getName());
    }

    public static class OtherLoopHelpers {

        @ExitCondition
        boolean enough(@V("count") int count) {
            return count >= 5;
        }
    }

    @Test
    void two_helper_objects_for_the_same_annotation_are_rejected() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(
                        CountingLoop.class, rootHelpers(CountingLoop.class, new LoopHelpers(), new OtherLoopHelpers())))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("@ExitCondition")
                .hasMessageContaining("reached")
                .hasMessageContaining("enough");
    }

    public interface GatedGreeting {

        @SequenceAgent(outputKey = "greeting", subAgents = Shouter.class)
        String run(@V("name") String name);

        @ActivationCondition
        static boolean always() {
            return true;
        }
    }

    @Test
    void static_and_instance_activation_conditions_without_value_are_rejected() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(
                        GatedGreeting.class, rootHelpers(GatedGreeting.class, new GreetingGate())))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("Only one @ActivationCondition method without value")
                .hasMessageContaining("always")
                .hasMessageContaining("allowed");
    }

    public interface LoopWithDefaultExit {

        @LoopAgent(outputKey = "count", maxIterations = 10, subAgents = Counter.class)
        int count(@V("count") int count);

        @ExitCondition
        default boolean reached(@V("count") int count) {
            return count >= 2;
        }
    }

    @Test
    void helper_object_of_the_agent_type_provides_the_non_static_helpers_of_the_type() {
        // the shape of a container bean: the agent is its own helper object
        LoopWithDefaultExit self = new LoopWithDefaultExit() {
            @Override
            public int count(int count) {
                throw new UnsupportedOperationException("implemented by the agentic system");
            }
        };

        LoopWithDefaultExit loop = AgenticServices.createAgenticSystem(
                LoopWithDefaultExit.class, rootHelpers(LoopWithDefaultExit.class, self));

        assertThat(loop.count(0)).isEqualTo(2);
    }

    @Test
    void null_helper_objects_are_rejected() {
        var configurator = new AgentConfigurator(ctx -> {}, null, null, null, type -> Arrays.asList((Object) null));

        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(CountingLoop.class, configurator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The helperObjectsResolver returned a null helper object for the agent");
    }
}
