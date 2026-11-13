package dev.langchain4j.test.service;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.Experimental;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.test.service.internal.InvocationRegistry;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

/**
 * Captures what AI Services do while a lambda runs: what they produce, which tools they execute, which sources they
 * retrieve, which model calls they make, how many tokens they use and how they end. Works with any AI Service method,
 * whatever it returns. To run the AI Service without a network, build it on a {@link MockChatModel} or a
 * {@link MockStreamingChatModel} that answers what the test tells it to.
 * <pre>{@code
 * import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
 * import static dev.langchain4j.test.service.AiServiceCapture.capture;
 *
 * AiServiceCapture capture = capture(() -> assistant.chat("Cancel booking MS-777"));
 *
 * assertThat(capture)
 *         .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
 *         .hasNoFailedToolExecutions();
 * assertThat(capture.outputText()).contains("cancelled");
 * }</pre>
 * {@link #output()} and {@link #outputText()} are what the AI Service produced, whatever the lambda returned: for
 * example the object of a method with structured output (also when the method returns it in a
 * {@code CompletableFuture}, a {@code Mono} or a {@code Uni}), or the text of the final response of a streaming method:
 * <pre>{@code
 * AiServiceCapture capture = capture(() -> assistant.extractBookingAsync("..."));
 * Booking booking = capture.output(Booking.class);
 * }</pre>
 * {@link #lambdaOutput()} is what the lambda returned, which can be something else, for example the result of a
 * controller called with {@code MockMvc}.
 * <p>
 * The top-level invocations (see {@link #invocations()}) are those not started by another AI Service
 * invocation, in the order they started: the AI Services that the lambda called, directly or through your code, and
 * the AI Services that a framework uses internally, for example the planner of an agentic supervisor. The invocations
 * started by their work (for example by their tools, guardrails or content retrievers) are nested in them (see
 * {@link AiServiceInvocation#nestedInvocations()}): an AI Service called by a tool is found there, not in
 * {@link #invocations()}. The shortcuts of this class, such as {@link #output()}, {@link #outputText()} and
 * {@link #lastRequest()}, are those of the only top-level invocation.
 *
 * <h2>Streaming and asynchronous methods</h2>
 * {@code capture(...)} returns when every captured invocation has finished, so streaming and asynchronous AI Service
 * methods need little extra code:
 * <ul>
 *     <li>a {@link TokenStream} must be started in the lambda: set the handlers it needs and call {@code start()}, for
 *     example {@code capture(() -> assistant.chatStream("Hello").ignoreErrors().start())}. If the lambda returns a
 *     {@code TokenStream} that was not started, {@code capture(...)} fails;</li>
 *     <li>a {@link Flow.Publisher} returned by the lambda (for example a Mutiny {@code Multi}), or a Reactive Streams
 *     publisher (for example a Reactor {@code Flux}), is subscribed to and read to the end, and its items are captured
 *     (see {@link #lambdaOutputItems()}). Do not return a publisher that the lambda already subscribed to: it would be
 *     subscribed to a second time, which may call the model again or fail;</li>
 *     <li>a {@code CompletableFuture} (or other {@code CompletionStage}) returned by the lambda does not need to be
 *     waited for;</li>
 *     <li>a Mutiny {@code Uni} returned by the lambda is subscribed to and waited for.</li>
 * </ul>
 * <pre>{@code
 * AiServiceCapture capture = capture(() -> assistant.chatStream("Hello").ignoreErrors().start());
 * String answer = capture.outputText();
 * }</pre>
 * While an invocation is still running, a warning naming it is logged every 30 seconds. After 10 minutes,
 * {@code capture(...)} cancels the future or publisher returned by the lambda and fails, naming the invocations that
 * have not finished. Change this timeout with the JVM system property
 * {@code langchain4j.test.capture.timeout-millis} (in milliseconds, {@code 0} for no timeout). {@code capture(...)}
 * also stops waiting when its thread is interrupted, for example by JUnit's {@code @Timeout}, and then cancels the
 * future or publisher returned by the lambda too. Call {@code capture(...)} on a thread that
 * may block, not on an event loop. If the future or publisher returned by the lambda has ended, but an invocation
 * still has not reported its end 5 seconds later, {@code capture(...)} fails, because some frameworks do not report
 * it in every case. If a {@code TokenStream} was created but is not started within 2 seconds, {@code capture(...)}
 * fails, because nothing would ever start it. Both waits can be changed, for example on a slow build machine, with
 * the JVM system properties {@code langchain4j.test.capture.end-report-grace-millis} and
 * {@code langchain4j.test.capture.stream-start-grace-millis} (in milliseconds), for example with {@code -D...} or the
 * {@code systemPropertyVariables} of the Maven Surefire plugin. These properties, and the timeout above, are not
 * Spring or Quarkus configuration properties: setting them in {@code application.properties} has no effect.
 *
 * <h2>Failures</h2>
 * If an AI Service invocation fails, {@code capture(...)} throws its error once all invocations have finished, also
 * when the error was passed to an {@code onError} handler or to a returned {@code CompletableFuture}, or was caught by
 * the lambda. Any other exception thrown by the lambda is rethrown too, for example an {@link AssertionError} thrown
 * in a mock model, or by an assertion in the lambda (such as {@code mockMvc.perform(...).andExpect(status().isOk())}).
 * An {@link Error} is rethrown even when a failure is expected. An exception or {@link AssertionError} thrown by a
 * tool can be different: whether it fails the invocation or is sent to the model as the result of the tool depends on
 * the kind of AI Service method and on its tool execution error handler (see
 * {@link AiServices#toolExecutionErrorHandler(ToolExecutionErrorHandler)}). When it is sent to the model, it is not
 * thrown: check it with {@link AiServiceInvocationAssert#hasToolExecutionFailed(String)} or
 * {@link AiServiceInvocationAssert#hasNoFailedToolExecutions()}. A failed invocation makes {@code capture(...)} throw
 * even when the code recovered from it, for example an agentic workflow whose error handler retries the agent or
 * returns a fallback result. To check a failure that the test expects, use {@link #captureExpectingFailure(Callable)}:
 * <pre>{@code
 * AiServiceCapture capture = captureExpectingFailure(() -> assistant.chat("Cancel MS-777"));
 *
 * assertThat(capture)
 *         .hasFailedWithMessageContaining("Booking MS-777 not found")
 *         .hasToolExecutionFailed("getBookingDetails");
 * }</pre>
 * A cancelled invocation does not make {@code capture(...)} throw, but every assertion on it fails, and
 * {@link AiServiceInvocation#output()} and {@link AiServiceInvocation#outputText()} throw. Invocations started by
 * another invocation (see {@link AiServiceInvocation#nestedInvocations()}) do not make {@code capture(...)} throw when
 * they fail, because the invocation that started them can handle their failure, for example a tool that reports the
 * error to the model. This relies on the AI Service reporting which invocation started another one (see
 * {@link InvocationContext#parentInvocationId()}).
 * <p>
 * Pay attention to what the invocation captured rather than to the side effects of your own handlers: for a
 * {@code TokenStream}, the invocation finishes before the {@code onCompleteResponse} handler you gave it has run, so
 * when {@code capture(...)} returns, that handler may still be running. {@link AiServiceInvocation#outputText()} has
 * the final text in any case.
 *
 * <h2>Threads</h2>
 * An invocation is captured when it starts on the thread that runs the lambda, or when it is started while a
 * captured invocation runs, for example by its tool, guardrail or content retriever (see
 * {@link AiServiceInvocation#nestedInvocations()}). Invocations started on other threads are not captured: for
 * example by an HTTP request to a running server, by an {@code @Async} method, by {@code subscribeOn(...)}, by a
 * parallel stream, by an executor, or the agents of an agentic workflow that run on other threads, such as the agents
 * of a parallel workflow. Captures on different threads do not see each other's invocations, so tests can run in
 * parallel. {@code langchain4j-test} listens to AI Services with listeners that ignore all events while nothing is
 * being captured.
 * <p>
 * {@code capture(...)} blocks until the captured invocations have finished, also when the lambda returns a
 * {@code CompletableFuture} or a stream. So call it from the test thread, never from an event-loop thread (for
 * example on a Vert.x context): an invocation that needs that thread to finish could then not finish.
 */
@Experimental
public final class AiServiceCapture {

    private static final String NOTHING_CAPTURED_HINT =
            """
            AI Service invocations are captured when they are started on the thread that runs the lambda, \
            for example by calling the AI Service directly. Invocations started on other threads are not captured: \
            for example by an HTTP request to a running server, an @Async method, subscribeOn(...) or \
            runSubscriptionOn(...), a parallel stream, an executor, a framework that moves the call to another \
            thread, or agents that run on other threads (such as the agents of a parallel workflow or agents marked \
            async). A TokenStream must be obtained from the AI Service inside the lambda, not only started \
            there. A publisher or a lazy value (such as a Flux, Multi, Mono or Uni) starts the AI Service only when \
            it is subscribed to: return it from the lambda or subscribe to it there. Nothing is captured \
            either when the AI Service is a mock (for example @MockitoBean or QuarkusMock), was not created by \
            LangChain4j's AiServices (for example in a test slice that does not load the LangChain4j \
            auto-configuration), or runs in another process (for example a packaged application tested with \
            @QuarkusIntegrationTest).""";

    private static final String NOT_LISTENING_HINT =
            """
            No AI Service in this JVM has the listeners of langchain4j-test registered. Possible causes: \
            the AI Service runs in another process (for example a packaged or native application tested with \
            @QuarkusIntegrationTest), the listeners are not registered for ServiceLoader in a native test image, \
            the AI Service is a mock (for example @MockitoBean or QuarkusMock) or was not created by LangChain4j's \
            AiServices (for example in a test slice that does not load the LangChain4j auto-configuration), no AI \
            Service was created yet (for example a lazily created bean that the lambda never called, such as \
            when a returned Uni was never subscribed to), a framework that loads the application with class loaders \
            of its own (such as the Quarkus extension) does not pass the listeners of langchain4j-test to \
            LangChain4j yet, langchain4j-core is older than langchain4j-test, or a custom \
            AiServiceListenerRegistrarFactory does not register the listeners it is given.""";

    static final Object LAMBDA_RETURNS_NOTHING = new Object();

    private final Object lambdaOutput;
    private final Throwable thrownByLambda;
    private final List<AiServiceInvocation> invocations;
    private final List<Object> lambdaOutputItems;

    /**
     * Runs the given lambda and captures the AI Service invocations it starts. If the lambda returns a publisher, a
     * future or a {@code Uni}, it is subscribed to. Then waits until all captured invocations have finished.
     *
     * @param code the code to run, for example {@code () -> assistant.chat("Hello")}
     * @return the captured invocations, and what the lambda returned
     */
    public static AiServiceCapture capture(Callable<?> code) {
        ensureNotNull(code, "code");
        return Capturer.capture(code, false);
    }

    /**
     * Runs the given lambda and captures the AI Service invocations it starts, then waits until all of them have
     * finished.
     *
     * @param code the code to run, for example {@code () -> assistant.chatStream("Hi").onPartialResponse(tokens::add).onError(errors::add).start()}
     * @return the captured invocations
     */
    public static AiServiceCapture capture(ThrowingRunnable code) {
        ensureNotNull(code, "code");
        return Capturer.capture(Capturer.supplier(code), false);
    }

    /**
     * Like {@link #capture(Callable)}, for a lambda that is expected to make an AI Service invocation fail: the
     * failed invocation is captured instead of its error being thrown, so that what happened before the failure can be
     * checked.
     *
     * @param code the code to run, for example {@code () -> assistant.chat("Cancel booking MS-777")}
     * @return the captured invocations, and what the lambda returned
     * @throws AssertionError if no AI Service invocation started by the lambda failed, also when nothing was captured
     *                        while the lambda threw an exception (the exception is then the cause)
     */
    public static AiServiceCapture captureExpectingFailure(Callable<?> code) {
        ensureNotNull(code, "code");
        return Capturer.capture(code, true);
    }

    /**
     * Like {@link #capture(ThrowingRunnable)}, for a lambda that is expected to make an AI Service invocation fail: the
     * failed invocation is captured instead of its error being thrown, so that what happened before the failure can be
     * checked.
     *
     * @param code the code to run, for example {@code () -> assistant.chatStream("Hi").onPartialResponse(tokens::add).onError(errors::add).start()}
     * @return the captured invocations
     * @throws AssertionError if no AI Service invocation started by the lambda failed, also when nothing was captured
     *                        while the lambda threw an exception (the exception is then the cause)
     */
    public static AiServiceCapture captureExpectingFailure(ThrowingRunnable code) {
        ensureNotNull(code, "code");
        return Capturer.capture(Capturer.supplier(code), true);
    }

    AiServiceCapture(
            Object lambdaOutput,
            Throwable thrownByLambda,
            List<AiServiceInvocation> invocations,
            List<Object> lambdaOutputItems) {
        this.lambdaOutput = lambdaOutput;
        this.thrownByLambda = thrownByLambda;
        this.invocations = List.copyOf(invocations);
        this.lambdaOutputItems = lambdaOutputItems == null ? null : List.copyOf(lambdaOutputItems);
    }

    /**
     * Returns what the only top-level AI Service invocation produced (see {@link AiServiceInvocation#output()}): for
     * example the {@code String} answer, the object of a method with structured output, the value inside a
     * {@code Result}, a {@code CompletableFuture}, a {@code Mono} or a {@code Uni}, or the text of the final response
     * of a streaming method. It does not depend on what the lambda returned (see {@link #lambdaOutput()} for that).
     * <pre>{@code
     * assertThat(capture.output()).isEqualTo(new Booking("MS-777"));
     * }</pre>
     * Use {@link #output(Class)} to get it as a given type, also when the lambda started several invocations.
     *
     * @throws AssertionError if no top-level invocation or more than one was captured, or if the invocation
     *                        failed or was cancelled
     */
    public Object output() {
        return single("Use output(Class) to get the output of a given type, or invocations()")
                .output();
    }

    /**
     * Returns what a top-level AI Service invocation produced, as the given type: the output of the only top-level
     * invocation whose output is of this type (see {@link AiServiceInvocation#output()}). This works also when the
     * lambda started several invocations, as long as only one of them produced a value of this type:
     * <pre>{@code
     * // the lambda extracts a Booking with one AI Service, then creates a Client with another one
     * Booking booking = capture.output(Booking.class);
     * Client client = capture.output(Client.class);
     * }</pre>
     * Invocations that failed or were cancelled produced nothing, so they never match.
     *
     * @param type the type of the output, for example {@code Booking.class} or {@code String.class}
     * @param <T>  the type of the output
     * @throws AssertionError if no top-level invocation or more than one produced a value of the given type
     */
    public <T> T output(Class<T> type) {
        ensureNotNull(type, "type");
        Class<?> boxed = boxed(type);
        List<AiServiceInvocation> matching = invocations.stream()
                .filter(invocation -> producedValue(invocation) && boxed.isInstance(invocation.output()))
                .toList();
        if (matching.size() == 1) {
            @SuppressWarnings("unchecked")
            T output = (T) matching.get(0).output();
            return output;
        }
        if (invocations.isEmpty()) {
            throw new AssertionError(nothingCaptured());
        }
        throw new AssertionError("Expected a single top-level AI Service invocation that produced a value of type %s, but %s. The top-level invocations produced: %s"
                .formatted(
                        type.getSimpleName(),
                        matching.isEmpty() ? "none did" : matching.size() + " did",
                        invocations.stream()
                                .map(invocation -> describe(invocation) + " -> " + describeOutput(invocation))
                                .collect(joining(", "))));
    }

    /**
     * Returns the text produced by the only top-level AI Service invocation: the answer of a method that returns a
     * {@code String} (also inside a {@code Result} or a {@code CompletableFuture}), or the text of the final response
     * of a streaming method. A shortcut for {@code invocation().outputText()}, see
     * {@link AiServiceInvocation#outputText()}.
     *
     * @throws AssertionError if no top-level invocation or more than one was captured, if the invocation failed
     *                        or was cancelled, or if the AI Service method does not produce text (see
     *                        {@link AiServiceInvocation#outputText()})
     */
    public String outputText() {
        return single("Use invocations() to choose one").outputText();
    }

    /**
     * Returns what the lambda returned. When the lambda returns the result of an AI Service method, this is the same as
     * {@link #output()}, except for a method that returns a {@code Result}, a stream or an asynchronous value (such as
     * a {@code TokenStream}, a {@code Flux}, a {@code CompletableFuture} or a {@code Uni}): then this is that
     * {@code Result} (whose content is {@link #output()}), or that stream or value, which has already been started and
     * has ended. Do not start or subscribe to it again: this would call the model again, or fail. When the lambda calls
     * something else, for example a controller with {@code MockMvc}, this is what that call returned:
     * <pre>{@code
     * AiServiceCapture capture = AiServiceCapture.capture(() -> mockMvc.perform(get("/chat?message=Hi")).andReturn());
     * MvcResult mvcResult = capture.lambdaOutput(MvcResult.class);
     * }</pre>
     *
     * @throws AssertionError if the lambda returns nothing (for example {@code () -> assistant.handle("...")}
     *                        with a {@code void} method), or if it threw an exception (possible with
     *                        {@link AiServiceCapture#captureExpectingFailure(Callable)})
     */
    public Object lambdaOutput() {
        if (thrownByLambda != null) {
            throw new AssertionError(
                    "The lambda threw " + thrownByLambda + ", so it returned no output", thrownByLambda);
        }
        if (lambdaOutput == LAMBDA_RETURNS_NOTHING) {
            throw new AssertionError(
                    "The lambda returns nothing, so it has no output. Use output() or outputText() for what the AI Service produced.");
        }
        return lambdaOutput;
    }

    /**
     * Returns what the lambda returned, as the given type. See {@link #lambdaOutput()}.
     *
     * @param type the type of the value returned by the lambda, for example {@code MvcResult.class}
     * @param <T>  the type of the value returned by the lambda
     * @throws AssertionError if the lambda threw an exception, or returned {@code null} or a value of another
     *                        type
     */
    public <T> T lambdaOutput(Class<T> type) {
        ensureNotNull(type, "type");
        Object output = lambdaOutput();
        if (!boxed(type).isInstance(output)) {
            throw new AssertionError("The lambda returned %s, not a %s"
                    .formatted(
                            output == null ? "null" : "a " + output.getClass().getName(), type.getName()));
        }
        @SuppressWarnings("unchecked")
        T typed = (T) output;
        return typed;
    }

    /**
     * Returns the items of the stream returned by the lambda, in the order they arrived. capture(...) subscribes to a
     * returned publisher (such as a {@code Flux}, a {@code Multi} or a {@link Flow.Publisher})
     * and keeps every item it receives, whatever its type: for example the {@code String}s streamed by an AI Service
     * method, or the server-sent events that a controller built from them.
     * <pre>{@code
     * AiServiceCapture capture = AiServiceCapture.capture(() -> streamingController.chat("Hi"));
     * assertThat(capture.lambdaOutputItems()).hasSizeGreaterThan(1);
     * }</pre>
     *
     * @throws AssertionError if the lambda did not return a stream, for example when it returned a
     *                        {@code TokenStream}, a {@code Mono} or a value, or when it threw an exception
     */
    public List<Object> lambdaOutputItems() {
        if (thrownByLambda != null) {
            throw new AssertionError(
                    "The lambda threw " + thrownByLambda + ", so it returned no stream", thrownByLambda);
        }
        if (lambdaOutputItems == null) {
            throw new AssertionError(
                    "The lambda returned %s, not a stream such as a Flux, Multi or Flow.Publisher"
                            .formatted(
                                    lambdaOutput == null || lambdaOutput == LAMBDA_RETURNS_NOTHING
                                            ? "nothing"
                                            : "a " + lambdaOutput.getClass().getSimpleName()));
        }
        return lambdaOutputItems;
    }

    /**
     * Returns the only top-level AI Service invocation (see {@link #invocations()}).
     *
     * @throws AssertionError if no top-level invocation or more than one was captured: use
     *                        {@link #invocations()} then
     */
    public AiServiceInvocation invocation() {
        return single("Use invocations() to choose one");
    }

    /**
     * Returns the top-level AI Service invocations: those not started by another captured invocation, in the order
     * they started. Empty if none was captured. The invocations started by their work (for example an AI Service
     * called by a tool) are nested in them, at any depth: see {@link AiServiceInvocation#nestedInvocations()}.
     */
    public List<AiServiceInvocation> invocations() {
        return invocations;
    }

    /**
     * Returns the only top-level AI Service invocation of the given AI Service interface (see {@link #invocations()}).
     * Useful when the lambda starts several invocations, for example an agentic workflow or supervisor, which calls
     * each of its agents as a separate top-level invocation:
     * <pre>{@code
     * AiServiceCapture capture = capture(() -> supervisor.invoke("Book a flight"));
     * assertThat(capture.invocation(BookingAgent.class)).hasToolExecuted("bookFlight");
     * }</pre>
     *
     * @param aiService the AI Service interface, for example {@code BookingAgent.class}
     * @throws AssertionError if no top-level invocation of this interface or more than one was captured: use
     *                        {@link #invocations(Class)} then
     */
    public AiServiceInvocation invocation(Class<?> aiService) {
        List<AiServiceInvocation> matching = invocations(aiService);
        if (matching.size() == 1) {
            return matching.get(0);
        }
        if (invocations.isEmpty()) {
            throw new AssertionError(nothingCaptured());
        }
        throw new AssertionError("Expected a single top-level invocation of %s, but %s. The top-level invocations are: %s"
                .formatted(
                        aiService.getName(),
                        matching.isEmpty() ? "there was none" : matching.size() + " were captured",
                        invocations.stream().map(AiServiceCapture::describe).collect(joining(", "))));
    }

    /**
     * Returns the top-level AI Service invocations of the given AI Service interface (see {@link #invocations()}), in
     * the order they started. Empty if none was captured.
     *
     * @param aiService the AI Service interface, for example {@code BookingAgent.class}
     */
    public List<AiServiceInvocation> invocations(Class<?> aiService) {
        ensureNotNull(aiService, "aiService");
        return invocations.stream()
                .filter(invocation -> aiService.getName().equals(invocation.invocationContext().interfaceName()))
                .toList();
    }

    /**
     * Returns the first request sent to the chat model by the only top-level AI Service invocation. A shortcut for
     * {@code invocation().firstRequest()}, see {@link AiServiceInvocation#firstRequest()}.
     * <pre>{@code
     * assertThat(capture.firstRequest()).hasSystemMessageContaining("support agent");
     * }</pre>
     *
     * @throws AssertionError if no top-level invocation or more than one was captured, or if the model did not
     *                        respond to any request of the invocation
     */
    public ChatRequest firstRequest() {
        return invocation().firstRequest();
    }

    /**
     * Returns the last request sent to the chat model by the only top-level AI Service invocation. A shortcut for
     * {@code invocation().lastRequest()}, see {@link AiServiceInvocation#lastRequest()}. Its messages are the
     * conversation that led to the last response, which can be given to a judge:
     * <pre>{@code
     * Judge.from(judgeModel)
     *         .assertThat(capture.outputText())
     *         .given(capture.lastRequest().messages())
     *         .satisfies("only uses facts from the tool results");
     * }</pre>
     *
     * @throws AssertionError if no top-level invocation or more than one was captured, or if the model did not
     *                        respond to any request of the invocation
     */
    public ChatRequest lastRequest() {
        return invocation().lastRequest();
    }

    /**
     * Returns the last response of the chat model to the only top-level AI Service invocation. A shortcut for
     * {@code invocation().lastResponse()}, see {@link AiServiceInvocation#lastResponse()}.
     *
     * @throws AssertionError if no top-level invocation or more than one was captured, or if the model did not
     *                        respond
     */
    public ChatResponse lastResponse() {
        return invocation().lastResponse();
    }

    private AiServiceInvocation single(String hint) {
        if (invocations.isEmpty()) {
            throw new AssertionError(nothingCaptured());
        }
        if (invocations.size() > 1) {
            throw new AssertionError("Expected a single top-level AI Service invocation, but %d were captured: %s. %s."
                    .formatted(
                            invocations.size(),
                            invocations.stream().map(AiServiceCapture::describe).collect(joining(", ")),
                            hint));
        }
        return invocations.get(0);
    }

    private static boolean producedValue(AiServiceInvocation invocation) {
        return invocation.error() == null && !invocation.isCancelled() && !invocation.returnsNothing();
    }

    private static String describeOutput(AiServiceInvocation invocation) {
        if (invocation.error() != null) {
            return "failed with " + invocation.error();
        }
        if (invocation.isCancelled()) {
            return "cancelled";
        }
        if (invocation.returnsNothing()) {
            return "nothing (the method returns void)";
        }
        Object output = invocation.output();
        return output == null ? "null" : output.getClass().getSimpleName();
    }

    private static Class<?> boxed(Class<?> type) {
        return type.isPrimitive() ? MethodType.methodType(type).wrap().returnType() : type;
    }

    private static String describe(AiServiceInvocation invocation) {
        return invocation.invocationContext().interfaceName() + "." + invocation.invocationContext().methodName();
    }

    static String nothingCaptured() {
        return "No AI Service invocation was captured. "
                + (InvocationRegistry.isListening() ? NOTHING_CAPTURED_HINT : NOT_LISTENING_HINT);
    }

    @Override
    public String toString() {
        return "AiServiceCapture{invocations=" + invocations + '}';
    }

    /**
     * A lambda to capture that returns nothing. It can throw checked exceptions; {@code capture(...)} rethrows them
     * unchanged, not wrapped, although it does not declare them. A lambda that returns a value is a {@link Callable},
     * whose checked exceptions are rethrown the same way.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Exception;
    }
}
