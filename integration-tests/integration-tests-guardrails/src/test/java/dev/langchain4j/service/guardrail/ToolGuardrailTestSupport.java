package dev.langchain4j.service.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ToolInputGuardrail;
import dev.langchain4j.guardrail.ToolInputGuardrailRequest;
import dev.langchain4j.guardrail.ToolInputGuardrailResult;
import dev.langchain4j.guardrail.ToolOutputGuardrail;
import dev.langchain4j.guardrail.ToolOutputGuardrailRequest;
import dev.langchain4j.guardrail.ToolOutputGuardrailResult;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.observability.api.event.AiServiceInvocationSummaryEvent;
import dev.langchain4j.observability.api.event.ToolCompensatedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.event.ToolInputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.ToolOutputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.listener.AiServiceInvocationSummaryListener;
import dev.langchain4j.observability.api.listener.ToolCompensatedEventListener;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.observability.api.listener.ToolInputGuardrailExecutedListener;
import dev.langchain4j.observability.api.listener.ToolOutputGuardrailExecutedListener;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Fixtures shared by the tool guardrail tests: a scripted model that calls tools, the four invocation modes of an AI
 * service behind one {@link #run(Mode, List, Consumer)} call, the four kinds of tools, and guardrails that work in
 * every mode.
 */
final class ToolGuardrailTestSupport {

    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final String MEMORY_ID = "user-1";
    static final String TEMPLATE = "Find {{topic}}";
    static final String TOPIC = "cats";
    static final String ORIGINAL_ARGUMENTS = "{\"q\":\"a\"}";
    static final String REWRITTEN_ARGUMENTS = "{\"q\":\"b\"}";
    static final ToolSpecification SEARCH =
            ToolSpecification.builder().name("search").description("Searches").build();

    // the result factories (failure, fatal) are default methods of the interfaces, which lambdas cannot call
    private static final ToolInputGuardrail INPUT = request -> null;
    private static final ToolOutputGuardrail OUTPUT = request -> null;

    private ToolGuardrailTestSupport() {}

    /** How the AI service is invoked. */
    enum Mode {
        SYNC,
        COMPLETABLE_FUTURE,
        TOKEN_STREAM,
        REACTIVE
    }

    /** How the {@code search} tool is registered. */
    enum ToolKind {
        /** A {@code @Tool} method. */
        ANNOTATED,
        /** {@code tools(Map)}. */
        MAP,
        /** {@code tools(List<AiServiceTool>)}. */
        LIST,
        /** A {@code ToolProvider}. */
        PROVIDER;

        void register(AiServices<?> services, SearchTool tool) {
            register(services, tool, builder -> {});
        }

        /**
         * @param customizer applied to the {@link AiServiceTool}, so only by the kinds that register one ({@link #LIST}
         *                   and {@link #PROVIDER}); the other two have no such surface
         */
        void register(AiServices<?> services, SearchTool tool, Consumer<AiServiceTool.Builder> customizer) {
            switch (this) {
                case ANNOTATED -> services.tools(new AnnotatedSearch(tool));
                case MAP -> services.tools(Map.of(SEARCH, tool));
                case LIST -> services.tools(List.<AiServiceTool>of(aiServiceTool(tool, customizer)));
                case PROVIDER ->
                    services.toolProvider(request -> ToolProviderResult.builder()
                            .add(aiServiceTool(tool, customizer))
                            .build());
            }
        }
    }

    static AiServiceTool aiServiceTool(SearchTool tool, Consumer<AiServiceTool.Builder> customizer) {
        var builder = AiServiceTool.builder().toolSpecification(SEARCH).toolExecutor(tool);
        customizer.accept(builder);
        return builder.build();
    }

    /**
     * Registers the tool with {@code tools(List<AiServiceTool>)}, after customizing its {@link AiServiceTool}. A typed
     * helper, because {@code services.tools(List.of(..))} is ambiguous with {@code tools(Collection<Object>)} inside a
     * lambda.
     */
    static void registerTool(AiServices<?> services, SearchTool tool, Consumer<AiServiceTool.Builder> customizer) {
        ToolKind.LIST.register(services, tool, customizer);
    }

    interface SyncAssistant {

        @UserMessage(TEMPLATE)
        Result<String> chat(@MemoryId String memoryId, @V("topic") String topic);
    }

    interface FutureAssistant {

        @UserMessage(TEMPLATE)
        CompletableFuture<String> chat(@MemoryId String memoryId, @V("topic") String topic);
    }

    interface StreamingAssistant {

        @UserMessage(TEMPLATE)
        TokenStream chat(@MemoryId String memoryId, @V("topic") String topic);
    }

    interface ReactiveAssistant {

        @UserMessage(TEMPLATE)
        Flow.Publisher<AiServiceStreamingEvent> chat(@MemoryId String memoryId, @V("topic") String topic);
    }

    /**
     * The {@code search} tool: records the arguments it ran with and answers with what its behavior says. It supports
     * the blocking and the asynchronous execution, since the {@code CompletableFuture} and reactive modes need the
     * latter.
     */
    static final class SearchTool implements ToolExecutor {

        final List<String> receivedArguments = new CopyOnWriteArrayList<>();
        private final Function<ToolExecutionRequest, ToolExecutionResult> behavior;

        private SearchTool(Function<ToolExecutionRequest, ToolExecutionResult> behavior) {
            this.behavior = behavior;
        }

        /** Answers {@code found <arguments>}. */
        static SearchTool echoing() {
            return returning(request -> "found " + request.arguments());
        }

        static SearchTool returning(String text) {
            return returning(request -> text);
        }

        static SearchTool returning(Function<ToolExecutionRequest, String> text) {
            return new SearchTool(request -> ToolExecutionResult.builder()
                    .resultText(text.apply(request))
                    .build());
        }

        static SearchTool returningContents(List<Content> contents) {
            return new SearchTool(request ->
                    ToolExecutionResult.builder().resultContents(contents).build());
        }

        static SearchTool failingWith(String message) {
            return new SearchTool(request -> {
                throw new IllegalStateException(message);
            });
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            receivedArguments.add(request.arguments());
            return behavior.apply(request);
        }

        @Override
        public CompletableFuture<ToolExecutionResult> executeAsync(
                ToolExecutionRequest request, InvocationContext context) {
            try {
                return CompletableFuture.completedFuture(executeWithContext(request, context));
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    /** The {@code search} tool as a {@code @Tool} method, delegating to a {@link SearchTool}. */
    public static class AnnotatedSearch {

        private final SearchTool tool;

        AnnotatedSearch(SearchTool tool) {
            this.tool = tool;
        }

        @Tool("Searches")
        public String search(@P(name = "q") String q) {
            // a @Tool method gets typed arguments, so the JSON the tool records is rebuilt from them
            var request = ToolExecutionRequest.builder()
                    .name("search")
                    .arguments("{\"q\":\"%s\"}".formatted(q))
                    .build();
            return tool.executeWithContext(request, null).resultText();
        }
    }

    /** Everything one invocation of the AI service reports. */
    static final class Run {

        final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
        final List<ToolExecution> afterToolExecutions = new CopyOnWriteArrayList<>();
        final List<ToolExecutedEvent> toolEvents = new CopyOnWriteArrayList<>();
        final List<ToolInputGuardrailExecutedEvent> inputGuardrailEvents = new CopyOnWriteArrayList<>();
        final List<ToolOutputGuardrailExecutedEvent> outputGuardrailEvents = new CopyOnWriteArrayList<>();
        final List<ToolCompensatedEvent> compensatedEvents = new CopyOnWriteArrayList<>();
        final List<AiServiceInvocationSummaryEvent> summaryEvents = new CopyOnWriteArrayList<>();
        final AtomicInteger errorHandlerCalls = new AtomicInteger();
        final CompletableFuture<String> answer = new CompletableFuture<>();
        Supplier<List<ChatRequest>> requests = List::of;
        /** Only in {@link Mode#SYNC}. */
        Result<String> result;
        /** Whether the call of the AI service method itself threw, instead of failing a future or {@code onError}. */
        boolean thrownByTheCall;

        /** The final answer of a successful invocation. */
        String answerText() {
            assertThat(answer).succeedsWithin(TIMEOUT);
            return answer.join();
        }

        /** The failure of the invocation: thrown, the cause of the failed future, or given to {@code onError}. */
        Throwable failure() {
            assertThat(answer).failsWithin(TIMEOUT);
            return answer.handle((ignored, error) -> unwrap(error)).join();
        }

        List<ChatRequest> modelRequests() {
            return requests.get();
        }

        /** The tool result message the LLM got in its second request (the one after the tool call), for the given tool. */
        ToolExecutionResultMessage toolResultMessageOfSecondRequest(String toolName) {
            return modelRequests().get(1).messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .filter(message -> message.toolName().equals(toolName))
                    .findFirst()
                    .orElseThrow();
        }

        /** All the tool result messages the LLM got in its second request, in the order of the request. */
        List<ToolExecutionResultMessage> toolResultMessagesOfSecondRequest() {
            return modelRequests().get(1).messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .toList();
        }

        /** The tool result messages chat memory holds, by tool name. */
        Map<String, ToolExecutionResultMessage> toolResultMessagesInMemory() {
            return memory.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .collect(Collectors.toMap(ToolExecutionResultMessage::toolName, message -> message));
        }

        /** Every message of every request that the LLM got, as text. */
        String everythingTheLlmReceived() {
            return modelRequests().stream()
                    .flatMap(request -> request.messages().stream())
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
        }

        List<String> compensatedTools() {
            return compensatedEvents.stream()
                    .map(event -> event.request().name())
                    .toList();
        }

        private static Throwable unwrap(Throwable error) {
            var cause = error;
            while (((cause instanceof CompletionException) || (cause instanceof ExecutionException))
                    && (cause.getCause() != null)) {
                cause = cause.getCause();
            }
            return cause;
        }
    }

    static ToolExecutionRequest toolCall(String id, String toolName, String arguments) {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(toolName)
                .arguments(arguments)
                .build();
    }

    static ToolExecutionRequest searchCall() {
        return toolCall("1", "search", ORIGINAL_ARGUMENTS);
    }

    /** The model calls {@code search}, then answers {@code done}. */
    static List<AiMessage> callingSearchThenAnswering() {
        return List.of(AiMessage.from(searchCall()), AiMessage.from("done"));
    }

    /** The model calls {@code search} {@code times} times, one call per round, then answers {@code done}. */
    static List<AiMessage> callingSearchThenAnswering(int times) {
        var messages = new ArrayList<AiMessage>();
        for (int i = 1; i <= times; i++) {
            messages.add(AiMessage.from(toolCall(String.valueOf(i), "search", ORIGINAL_ARGUMENTS)));
        }
        messages.add(AiMessage.from("done"));
        return messages;
    }

    static Run run(Mode mode, Consumer<AiServices<?>> configuration) {
        return run(mode, callingSearchThenAnswering(), configuration);
    }

    /**
     * Invokes an AI service in the given mode against a model that answers with the given messages in turn, and waits
     * for the invocation to end, normally or not.
     *
     * @param configuration registers the tools and guardrails, and any other setting of the test
     */
    static Run run(Mode mode, List<AiMessage> responses, Consumer<AiServices<?>> configuration) {
        var run = new Run();

        switch (mode) {
            case SYNC -> {
                var model = MockChatModel.thatResponds(responses);
                run.requests = model::requests;
                var services = configure(AiServices.builder(SyncAssistant.class).chatModel(model), run);
                configuration.accept(services);
                try {
                    run.result = services.build().chat(MEMORY_ID, TOPIC);
                    run.answer.complete(run.result.content());
                } catch (RuntimeException e) {
                    run.thrownByTheCall = true;
                    run.answer.completeExceptionally(e);
                }
            }
            case COMPLETABLE_FUTURE -> {
                var model = MockChatModel.thatResponds(responses);
                run.requests = model::requests;
                var services =
                        configure(AiServices.builder(FutureAssistant.class).chatModel(model), run);
                configuration.accept(services);
                try {
                    services.build().chat(MEMORY_ID, TOPIC).whenComplete((answer, error) -> {
                        if (error != null) {
                            run.answer.completeExceptionally(error);
                        } else {
                            run.answer.complete(answer);
                        }
                    });
                } catch (RuntimeException e) {
                    run.thrownByTheCall = true;
                    run.answer.completeExceptionally(e);
                }
            }
            case TOKEN_STREAM -> {
                var model = MockStreamingChatModel.thatResponds(responses);
                run.requests = model::requests;
                var services =
                        configure(AiServices.builder(StreamingAssistant.class).streamingChatModel(model), run);
                configuration.accept(services);
                services.build()
                        .chat(MEMORY_ID, TOPIC)
                        .onPartialResponse(ignored -> {})
                        .onCompleteResponse(response ->
                                run.answer.complete(response.aiMessage().text()))
                        .onError(run.answer::completeExceptionally)
                        .start();
            }
            case REACTIVE -> {
                var model = MockStreamingChatModel.thatResponds(responses);
                run.requests = model::requests;
                var services =
                        configure(AiServices.builder(ReactiveAssistant.class).streamingChatModel(model), run);
                configuration.accept(services);
                services.build().chat(MEMORY_ID, TOPIC).subscribe(new AnswerSubscriber(run.answer));
            }
        }

        run.answer
                .handle((ignored, ignoredError) -> null)
                .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();
        return run;
    }

    private static <T> AiServices<T> configure(AiServices<T> services, Run run) {
        return services.chatMemoryProvider(memoryId -> run.memory)
                .afterToolExecution(run.afterToolExecutions::add)
                // a custom handler, to prove that a fatal guardrail failure bypasses it
                .toolExecutionErrorHandler((error, context) -> {
                    run.errorHandlerCalls.incrementAndGet();
                    return ToolErrorHandlerResult.text(error.getMessage());
                })
                .registerListener((ToolExecutedEventListener) run.toolEvents::add)
                .registerListener((ToolInputGuardrailExecutedListener) run.inputGuardrailEvents::add)
                .registerListener((ToolOutputGuardrailExecutedListener) run.outputGuardrailEvents::add)
                .registerListener((ToolCompensatedEventListener) run.compensatedEvents::add)
                .registerListener((AiServiceInvocationSummaryListener) run.summaryEvents::add);
    }

    /** Completes {@code answer} with the final response of a reactive invocation, or with its failure. */
    private static final class AnswerSubscriber implements Flow.Subscriber<AiServiceStreamingEvent> {

        private final CompletableFuture<String> answer;

        AnswerSubscriber(CompletableFuture<String> answer) {
            this.answer = answer;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(AiServiceStreamingEvent event) {
            if (event instanceof AiServiceStreamingEvent.FinalResponseEvent finalResponse) {
                answer.complete(finalResponse.chatResponse().aiMessage().text());
            }
        }

        @Override
        public void onError(Throwable error) {
            answer.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            answer.complete(null);
        }
    }

    // ---------- guardrails that work in every mode ----------

    /**
     * An input guardrail that validates asynchronously, as the {@code CompletableFuture} and reactive modes require,
     * and in a blocking way for the other two.
     */
    static ToolInputGuardrail input(Function<ToolInputGuardrailRequest, ToolInputGuardrailResult> validation) {
        return new ToolInputGuardrail() {
            @Override
            public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
                return validation.apply(request);
            }

            @Override
            public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    /** The output counterpart of {@link #input(Function)}. */
    static ToolOutputGuardrail output(Function<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> validation) {
        return new ToolOutputGuardrail() {
            @Override
            public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
                return validation.apply(request);
            }

            @Override
            public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
                return CompletableFuture.completedFuture(validate(request));
            }
        };
    }

    /** An input guardrail for one tool: the other tools pass. */
    static ToolInputGuardrail onInputOf(
            String toolName, Function<ToolInputGuardrailRequest, ToolInputGuardrailResult> action) {
        return input(request -> toolName.equals(request.toolName()) ? action.apply(request) : INPUT.success());
    }

    /** An output guardrail for one tool: the other tools pass. */
    static ToolOutputGuardrail onOutputOf(
            String toolName, Function<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> action) {
        return output(request -> toolName.equals(request.toolName()) ? action.apply(request) : OUTPUT.success());
    }

    static ToolInputGuardrailResult inputFailure(String message) {
        return INPUT.failure(message);
    }

    static ToolInputGuardrailResult inputFatal(String message) {
        return INPUT.fatal(message);
    }

    static ToolOutputGuardrailResult outputFailure(String message) {
        return OUTPUT.failure(message);
    }

    static ToolOutputGuardrailResult outputFatal(String message) {
        return OUTPUT.fatal(message);
    }

    /** A rewrite of the arguments that appends {@code suffix} to the query, so a chain of rewrites shows its order. */
    static ToolInputGuardrailResult appendToQuery(ToolInputGuardrailRequest request, String suffix) {
        return ToolInputGuardrailResult.successWith(
                "{\"q\":\"%s%s\"}".formatted(request.argumentsAsMap().get("q"), suffix));
    }

    /** A rewrite of the result text that appends {@code suffix}, so a chain of rewrites shows its order. */
    static ToolOutputGuardrailResult appendToText(ToolOutputGuardrailRequest request, String suffix) {
        return ToolOutputGuardrailResult.successWith(request.text() + suffix);
    }

    /** A class-based input guardrail: the base class implements the asynchronous validation like the blocking one. */
    abstract static class BlockingToolInputGuardrail implements ToolInputGuardrail {

        @Override
        public CompletableFuture<ToolInputGuardrailResult> validateAsync(ToolInputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    /** A class-based output guardrail: the base class implements the asynchronous validation like the blocking one. */
    abstract static class BlockingToolOutputGuardrail implements ToolOutputGuardrail {

        @Override
        public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
            return CompletableFuture.completedFuture(validate(request));
        }
    }

    static List<Content> texts(String... texts) {
        return Arrays.stream(texts).<Content>map(TextContent::from).toList();
    }
}
