package dev.langchain4j.test.service;

import static dev.langchain4j.internal.ToolArgumentsParser.argumentsAsMap;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNegative;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.test.LangChain4jAssertions;
import dev.langchain4j.test.judge.Judge;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;

/**
 * Assertions on what an AI Service did: which tools were executed, with which arguments and in which order, which
 * sources were retrieved, how many times the chat model and the other models were called, why the response finished
 * and whether the invocation succeeded. These assertions are deterministic: they do not call a model. To check
 * the response text with natural-language criteria, using a model as a judge, see {@link Judge}.
 * <p>
 * Capture the invocation with {@link AiServiceCapture}, which works with any AI Service method:
 * <pre>{@code
 * import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
 *
 * AiServiceCapture capture = AiServiceCapture.capture(() -> assistant.chat("Cancel my booking MS-777"));
 *
 * assertThat(capture)
 *         .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking")
 *         .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"))
 *         .hasNoFailedToolExecutions();
 * }</pre>
 * This works for every return type of the AI Service method, also {@code Result<...>}: capture the call.
 * <p>
 * {@link #hasToolExecuted(String)}, {@link #hasToolExecutedTimes(String, int)},
 * {@link #hasToolsExecutedInOrder(String...)}, {@link #hasToolsExecutedExactlyInAnyOrder(String...)} and
 * both {@code hasToolExecutedWith(...)} count only the tool executions that succeeded. A tool that threw an
 * exception counts as a failed execution, also when the AI Service sent the error to the model and then succeeded:
 * check it with
 * {@link #hasToolExecutionFailed(String)} or {@link #hasNoFailedToolExecutions()}. They fail if the AI Service did not
 * report whether its tool executions failed (see {@link ToolExecutedEvent#isError()}).
 * <p>
 * When the invocation started other AI Service invocations (for example an AI Service called by one of its tools, see
 * {@link AiServiceInvocation#nestedInvocations()}), say which ones to check first: {@link #includingNested()} checks
 * the whole tree, {@link #topLevelOnly()} only what the invocation did itself. Without it, the assertions on tools,
 * sources and model calls fail, because both readings are plausible and a wrong one could hide a
 * problem: a sub-agent that ran a forbidden tool, or called the model many times. An invocation without nested invocations needs
 * neither.
 * <p>
 * Like Mockito's {@code verifyNoMoreInteractions}, {@link #hasNoMoreToolExecutions()} checks that every tool
 * execution was checked by a tool assertion before, also by an earlier {@code assertThat(...)} on the same capture:
 * <pre>{@code
 * assertThat(capture).hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-777"));
 * assertThat(capture).hasToolExecuted("getBookingDetails").hasNoMoreToolExecutions();
 * }</pre>
 * <p>
 * When an assertion fails, the message names the AI Service method and lists all executed tools with their
 * arguments and results.
 * All assertions fail if the invocation was cancelled. If the invocation failed, they fail until
 * {@link #hasFailedWithMessageContaining(String)} or {@link #hasFailedWith(Class)} has checked the failure; after
 * it, they check what happened before the failure.
 * <p>
 * These assertions need the {@code langchain4j} module, where AI Services are defined. {@code langchain4j-test}
 * does not bring it in. If the compiler reports that a class of {@code dev.langchain4j.service} is not found, add it,
 * with the same version as your other LangChain4j modules (managed by {@code langchain4j-bom} if you import it):
 * <pre>{@code
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j</artifactId>
 * </dependency>
 * }</pre>
 */
@Experimental
public final class AiServiceInvocationAssert
        extends AbstractObjectAssert<AiServiceInvocationAssert, AiServiceInvocation> {

    private static final int MAX_TEXT_LENGTH = 300;

    private enum Scope {
        TOP_LEVEL_ONLY,
        INCLUDING_NESTED
    }

    private boolean failureChecked;
    private Scope scope;

    /**
     * Creates assertions on the given AI Service invocation. Prefer
     * {@link LangChain4jAssertions#assertThat(AiServiceInvocation)}.
     */
    public AiServiceInvocationAssert(AiServiceInvocation actual) {
        super(actual, AiServiceInvocationAssert.class);
        if (actual != null) {
            describedAs(methodName(actual.invocationContext()));
        }
    }

    /**
     * Makes the assertions that follow check the whole tree of invocations: this invocation and all the invocations
     * it started, directly or not (see {@link AiServiceInvocation#nestedInvocations()}). Their tool executions,
     * sources and model calls add up.
     */
    public AiServiceInvocationAssert includingNested() {
        scope = Scope.INCLUDING_NESTED;
        return this;
    }

    /**
     * Makes the assertions that follow check only what this invocation did itself, not the invocations it started
     * (see {@link AiServiceInvocation#nestedInvocations()}).
     */
    public AiServiceInvocationAssert topLevelOnly() {
        scope = Scope.TOP_LEVEL_ONLY;
        return this;
    }

    /**
     * Asserts that no tool was executed, not even one that failed.
     */
    public AiServiceInvocationAssert hasNoToolExecutions() {
        ensureSucceeded();
        if (!scopedExecutions().isEmpty()) {
            throw failure("Expected no tools to be executed, but these were executed:%n%s", describeToolExecutions());
        }
        return this;
    }

    /**
     * Asserts that the tool with the given name was executed successfully at least once. Marks all its successful
     * executions as checked (see {@link #hasNoMoreToolExecutions()}).
     */
    public AiServiceInvocationAssert hasToolExecuted(String toolName) {
        ensureNotBlank(toolName, "toolName");
        ensureSucceeded();
        String expectation = "Expected tool <%s> to be executed successfully".formatted(toolName);
        List<ScopedExecution> matching = succeeded(expectation).stream()
                .filter(execution -> execution.isOf(toolName))
                .toList();
        if (matching.isEmpty()) {
            long failed = failedTimes(toolName);
            throw failure(
                    "%s, but %s. %s",
                    expectation,
                    failed > 0 ? "its %d execution(s) failed".formatted(failed) : "it was not executed",
                    describeToolExecutions());
        }
        matching.forEach(ScopedExecution::markVerified);
        return this;
    }

    /**
     * Asserts that the tool with the given name was executed at least once and that this execution failed: the tool
     * threw an exception, or its arguments were invalid. Use it to check how the AI Service handles a failing tool.
     * Marks all failed executions of the tool as checked (see {@link #hasNoMoreToolExecutions()}).
     */
    public AiServiceInvocationAssert hasToolExecutionFailed(String toolName) {
        ensureNotBlank(toolName, "toolName");
        ensureSucceeded();
        String expectation = "Expected an execution of tool <%s> to fail".formatted(toolName);
        ensureToolFailuresReported(expectation);
        List<ScopedExecution> failed = scopedExecutions().stream()
                .filter(execution ->
                        execution.isOf(toolName) && execution.toolExecution().hasFailed())
                .toList();
        if (failed.isEmpty()) {
            throw failure(
                    "%s, but %s. %s",
                    expectation,
                    toolNames().contains(toolName) ? "none failed" : "it was not executed",
                    describeToolExecutions());
        }
        failed.forEach(ScopedExecution::markVerified);
        return this;
    }

    /**
     * Asserts that the tool with the given name was executed successfully exactly the given number of times.
     * {@code 0} checks that it was not executed successfully. Marks its successful executions as checked (see
     * {@link #hasNoMoreToolExecutions()}).
     */
    public AiServiceInvocationAssert hasToolExecutedTimes(String toolName, int times) {
        ensureNotBlank(toolName, "toolName");
        ensureNotNegative(times, "times");
        ensureSucceeded();
        String expectation = "Expected tool <%s> to be executed successfully %d time(s)".formatted(toolName, times);
        List<ScopedExecution> matching = succeeded(expectation).stream()
                .filter(execution -> execution.isOf(toolName))
                .toList();
        if (matching.size() != times) {
            long failed = failedTimes(toolName);
            throw failure(
                    "%s, but it was %d time(s)%s. %s",
                    expectation,
                    matching.size(),
                    failed > 0 ? " and failed %d time(s)".formatted(failed) : "",
                    describeToolExecutions());
        }
        matching.forEach(ScopedExecution::markVerified);
        return this;
    }

    /**
     * Asserts that the given tools were executed successfully in the given order. Other tools may have been executed
     * before, between or after them, for example {@code hasToolsExecutedInOrder("getBookingDetails", "cancelBooking")}
     * checks that the booking was looked up before it was cancelled. When the model asked for several tools in one
     * response (parallel tool calls), their order is the order in which the model listed them: it does not show that
     * one tool used the result of the other. To check that exactly these tools were executed, in this order, use
     * {@code executedToolNames().containsExactly(...)} (this includes failed executions). Marks the executions that
     * matched as checked (see {@link #hasNoMoreToolExecutions()}).
     *
     * @throws IllegalStateException after {@link #includingNested()}: the order of the tool executions of different
     *                               invocations is not known
     */
    public AiServiceInvocationAssert hasToolsExecutedInOrder(String... toolNames) {
        ensureNotNull(toolNames, "toolNames");
        ensureSucceeded();
        String expectation = "Expected these tools to be executed successfully in this order: %s"
                .formatted(Arrays.asList(toolNames));
        List<ScopedExecution> executions = succeeded(expectation);
        if (scope == Scope.INCLUDING_NESTED && !actual.nestedInvocations().isEmpty()) {
            throw new IllegalStateException(
                    "The order of the tool executions of different invocations is not known. "
                            + "Use topLevelOnly(), or check a nested invocation: assertThat(invocation.nestedInvocations().get(i))");
        }
        List<ScopedExecution> matched = new ArrayList<>();
        for (ScopedExecution execution : executions) {
            if (matched.size() < toolNames.length && execution.isOf(toolNames[matched.size()])) {
                matched.add(execution);
            }
        }
        if (matched.size() < toolNames.length) {
            throw failure("%s, but they were not. %s", expectation, describeToolExecutions());
        }
        matched.forEach(ScopedExecution::markVerified);
        return this;
    }

    /**
     * Asserts that exactly the given tools were executed successfully, in any order, and no other tools: a tool that
     * is not given must not have been executed at all, not even in an execution that failed. Failed executions of the
     * given tools are not counted: check them with {@link #hasNoFailedToolExecutions()}.
     * A tool executed several times must be given as many times:
     * {@code hasToolsExecutedExactlyInAnyOrder("getWeather", "getWeather")} passes only when {@code getWeather} was
     * executed twice and no other tool was executed. To also check the order, use
     * {@code executedToolNames().containsExactly(...)} (this includes failed executions). Marks all successful
     * executions as checked (see {@link #hasNoMoreToolExecutions()}).
     */
    public AiServiceInvocationAssert hasToolsExecutedExactlyInAnyOrder(String... toolNames) {
        ensureNotNull(toolNames, "toolNames");
        ensureSucceeded();
        List<String> expected = new ArrayList<>(Arrays.asList(toolNames));
        List<ScopedExecution> executions =
                succeeded("Expected exactly these successful tool executions (in any order): " + expected);
        List<String> unexpected = new ArrayList<>(
                executions.stream().map(ScopedExecution::toolName).toList());
        List<String> missing = new ArrayList<>();
        for (String name : expected) {
            if (!unexpected.remove(name)) {
                missing.add(name);
            }
        }
        List<String> unexpectedFailed = scopedExecutions().stream()
                .filter(execution -> execution.toolExecution().hasFailed())
                .map(ScopedExecution::toolName)
                .filter(name -> !expected.contains(name))
                .toList();
        if (!missing.isEmpty() || !unexpected.isEmpty() || !unexpectedFailed.isEmpty()) {
            throw failure(
                    "Expected exactly these successful tool executions (in any order): %s%nbut these were missing: %s%nand these were not expected: %s%nand these tools, not expected, were executed and failed: %s%n%s",
                    expected, missing, unexpected, unexpectedFailed, describeToolExecutions());
        }
        executions.forEach(ScopedExecution::markVerified);
        return this;
    }

    /**
     * Asserts that the tool with the given name was executed successfully at least once with arguments that contain
     * all the given arguments. Other arguments are ignored. Values are compared as JSON: {@code Map.of("count", 3)}
     * matches an argument {@code "count": 3} as well as {@code "count": 3.0}. Nested objects and arrays must match
     * exactly. A number or a boolean sent as a string matches too, as the tool receives it converted to the type of its
     * parameter: {@code Map.of("count", 3)} matches {@code "count": "3"}, and {@code Map.of("urgent", true)} matches
     * {@code "urgent": "true"}. Text must match exactly: when a real model can write a value in several ways, use
     * {@link #hasToolExecutedWith(String, Consumer)}.
     * <p>
     * The argument names are the names of the tool method's parameters. Compile with {@code -parameters} or name
     * the parameters with {@code @P(name = ...)}, otherwise they are {@code arg0}, {@code arg1} and so on.
     * <p>
     * Marks the matching executions as checked (see {@link #hasNoMoreToolExecutions()}).
     */
    public AiServiceInvocationAssert hasToolExecutedWith(String toolName, Map<String, ?> expectedArguments) {
        ensureNotBlank(toolName, "toolName");
        ensureNotNull(expectedArguments, "expectedArguments");
        ensureSucceeded();
        String expectation = "Expected tool <%s> to be executed successfully with arguments containing %s"
                .formatted(toolName, Json.toJson(expectedArguments));
        ensureToolFailuresReported(expectation);
        Map<?, ?> expected = Json.fromJson(Json.toJson(expectedArguments), Map.class);
        List<ScopedExecution> matching = scopedExecutions().stream()
                .filter(execution -> {
                    Map<?, ?> arguments = parseArguments(execution.toolExecution());
                    return execution.isOf(toolName) && arguments != null && containsAll(arguments, expected);
                })
                .toList();
        List<ScopedExecution> succeeded = matching.stream()
                .filter(execution -> !execution.toolExecution().hasFailed())
                .toList();
        if (succeeded.isEmpty()) {
            throw failure(
                    "%s, but %s. %s",
                    expectation,
                    matching.isEmpty() ? "it was not" : "its executions with them failed",
                    describeToolExecutions());
        }
        succeeded.forEach(ScopedExecution::markVerified);
        return this;
    }

    /**
     * Asserts that the tool with the given name was executed successfully at least once with arguments that pass the
     * given requirements. Use it when a real model can write an argument in several ways, for example {@code "Munich"}
     * or {@code "München"}, so that {@link #hasToolExecutedWith(String, Map)} would fail for a correct call. Accept only
     * what your tool can really handle: a looser check can hide a call that the tool cannot process.
     * <pre>{@code
     * assertThat(capture).hasToolExecutedWith("getWeather", arguments -> {
     *     assertThat(arguments.get("city")).isIn("Munich", "München");
     *     Judge.from(judgeModel).assertThat(arguments.get("country").toString()).satisfies("refers to Germany");
     * });
     * }</pre>
     * The requirements receive the arguments as the model sent them, parsed from JSON: a number is a {@link Number}, a
     * nested object a {@link Map} and an array a {@link List}. They run once for every successful execution of the
     * tool, and an execution passes unless they throw an {@link AssertionError}. Any other exception is rethrown, for
     * example when a judge cannot give a verdict.
     * <p>
     * Marks the executions that passed as checked (see {@link #hasNoMoreToolExecutions()}).
     */
    public AiServiceInvocationAssert hasToolExecutedWith(
            String toolName, Consumer<Map<String, Object>> argumentsRequirements) {
        ensureNotBlank(toolName, "toolName");
        ensureNotNull(argumentsRequirements, "argumentsRequirements");
        ensureSucceeded();
        String expectation = "Expected tool <%s> to be executed successfully with arguments that pass the requirements"
                .formatted(toolName);
        List<ScopedExecution> passed = new ArrayList<>();
        List<String> rejections = new ArrayList<>();
        for (ScopedExecution execution : succeeded(expectation)) {
            Map<String, Object> arguments = parseArguments(execution.toolExecution());
            if (!execution.isOf(toolName) || arguments == null) {
                continue;
            }
            try {
                argumentsRequirements.accept(arguments);
                passed.add(execution);
            } catch (AssertionError rejection) {
                rejections.add(rejection.getMessage());
            }
        }
        if (passed.isEmpty()) {
            throw failure(
                    "%s, but %s. %s",
                    expectation,
                    rejections.isEmpty()
                            ? "it was not"
                            : "its executions did not pass them:%n%s"
                                    .formatted(String.join(System.lineSeparator(), rejections)),
                    describeToolExecutions());
        }
        passed.forEach(ScopedExecution::markVerified);
        return this;
    }

    /**
     * Asserts that every tool execution was checked by one of the tool assertions above, called before on the same
     * invocation, like Mockito's {@code verifyNoMoreInteractions}: the assertions add up across separate
     * {@code assertThat(...)} statements on the same {@link AiServiceCapture}. Use it to check that the AI Service
     * executed no other tools than the expected ones, also with other arguments or failing.
     */
    public AiServiceInvocationAssert hasNoMoreToolExecutions() {
        ensureSucceeded();
        List<ScopedExecution> unchecked = scopedExecutions().stream()
                .filter(execution -> !execution.isVerified())
                .toList();
        if (!unchecked.isEmpty()) {
            throw failure(
                    "Expected no more tool executions, but these were not checked by a tool assertion:%n%s%n%s",
                    describe(unchecked), describeToolExecutions());
        }
        return this;
    }

    /**
     * Asserts that no tool execution failed (see {@link ToolExecution#hasFailed()}). Fails if the AI Service did not
     * report whether its tool executions failed (see {@link ToolExecutedEvent#isError()}). A tool that throws an
     * exception, or an {@link AssertionError} in a test, counts as a failed tool execution when the AI Service sends
     * the error to the model instead of failing. Whether it does depends on the kind of AI Service method and on its
     * tool execution error handler (see {@link AiServices#toolExecutionErrorHandler(ToolExecutionErrorHandler)}).
     */
    public AiServiceInvocationAssert hasNoFailedToolExecutions() {
        ensureSucceeded();
        ensureToolFailuresReported("Expected no failed tool executions");
        if (scopedExecutions().stream()
                .anyMatch(execution -> execution.toolExecution().hasFailed())) {
            throw failure("Expected no failed tool executions, but some failed. %s", describeToolExecutions());
        }
        return this;
    }

    /**
     * Asserts that at least one retrieved source (see {@link AiServiceInvocation#sources()}) contains the given text
     * (case-sensitive). Fails if no sources were captured.
     */
    public AiServiceInvocationAssert hasRetrievedSourceContaining(String text) {
        ensureNotBlank(text, "text");
        ensureSucceeded();
        List<Content> sources = scopedSources("Expected a retrieved source containing <%s>".formatted(text));
        boolean found = sources.stream()
                .map(Content::textSegment)
                .filter(Objects::nonNull)
                .anyMatch(segment -> segment.text().contains(text));
        if (!found) {
            String description = sources.isEmpty()
                    ? "No sources were retrieved, or the AI Service has no retrieval augmentor."
                    : "Retrieved sources:%n%s".formatted(describeSources(sources));
            throw failure("Expected a retrieved source containing <%s>, but there was none. %s", text, description);
        }
        return this;
    }

    /**
     * Asserts that no sources were retrieved for retrieval-augmented generation (RAG), for example for a question
     * that the retrieved knowledge should not answer. Fails if the AI Service has no retrieval augmentor or did not
     * report what it retrieved (see {@link AiServiceInvocation#sources()}).
     */
    public AiServiceInvocationAssert hasNoRetrievedSources() {
        ensureSucceeded();
        List<Content> sources = scopedSources("Expected no retrieved sources");
        if (!sources.isEmpty()) {
            throw failure("Expected no retrieved sources, but these were retrieved:%n%s", describeSources(sources));
        }
        return this;
    }

    /**
     * Asserts that the AI Service called its own chat model exactly the given number of times (see
     * {@link AiServiceInvocation#ownChatModelCalls()}), also calls that failed. For example, an AI Service that
     * executes tools once calls its chat model twice: once for the tool calls and once more for the final answer. To
     * also count the calls of the components the AI Service runs (for example an embedding or a moderation model), use
     * {@link #hasModelCallCount(int)}.
     */
    public AiServiceInvocationAssert hasOwnChatModelCallCount(int expectedCount) {
        ensureNotNegative(expectedCount, "expectedCount");
        ensureSucceeded();
        int actualCount = scopedOwnChatModelCalls().size();
        if (actualCount != expectedCount) {
            throw failure(
                    "Expected %d call(s) of the AI Service to its chat model, but there were %d. %s",
                    expectedCount, actualCount, describeToolExecutions());
        }
        return this;
    }

    /**
     * Asserts that the AI Service called its own chat model at most the given number of times (see
     * {@link AiServiceInvocation#ownChatModelCalls()}), also calls that failed, for example to check that the AI
     * Service does not call tools in a loop.
     */
    public AiServiceInvocationAssert hasOwnChatModelCallCountAtMost(int maxCount) {
        ensureNotNegative(maxCount, "maxCount");
        ensureSucceeded();
        int actualCount = scopedOwnChatModelCalls().size();
        if (actualCount > maxCount) {
            throw failure(
                    "Expected at most %d call(s) of the AI Service to its chat model, but there were %d. %s",
                    maxCount, actualCount, describeToolExecutions());
        }
        return this;
    }

    /**
     * Asserts that the final response of this invocation finished for the given reason. For example,
     * {@link FinishReason#LENGTH} means that the response was cut off because it reached the maximum number of output
     * tokens, so {@code hasFinishReason(FinishReason.STOP)} checks that the response is complete. It is about the
     * final answer of this invocation, so nested invocations are never checked.
     */
    public AiServiceInvocationAssert hasFinishReason(FinishReason finishReason) {
        ensureNotNull(finishReason, "finishReason");
        ensureSucceeded();
        if (actual.finishReason() == null) {
            if (actual.chatModelNotifiedNoListeners()) {
                throw failure("Expected finish reason <%s>, but %s", finishReason, actual.notNotifiedMessage());
            }
            throw failure("Expected finish reason <%s>, but the model did not report it", finishReason);
        }
        if (actual.finishReason() != finishReason) {
            throw failure("Expected finish reason <%s>, but was <%s>", finishReason, actual.finishReason());
        }
        return this;
    }

    /**
     * Asserts that exactly the given number of model calls were made (see {@link AiServiceInvocation#modelCalls()}):
     * those of the AI Service to its own chat model and those of the components it runs, of every model type, also
     * calls that failed. To count only the calls of the AI Service to its own chat model, use
     * {@link #hasOwnChatModelCallCount(int)}.
     */
    public AiServiceInvocationAssert hasModelCallCount(int expectedCount) {
        return hasModelCallCount(ModelCall.class, expectedCount);
    }

    /**
     * Asserts that exactly the given number of calls were made to models of the given type (see
     * {@link AiServiceInvocation#modelCalls()}), also calls that failed. For example,
     * {@code hasModelCallCount(EmbeddingModelCall.class, 1)} checks that the query was embedded once.
     *
     * @param type the type of model call, for example {@code ChatModelCall.class} or {@code EmbeddingModelCall.class}
     */
    public AiServiceInvocationAssert hasModelCallCount(Class<? extends ModelCall> type, int expectedCount) {
        ensureNotNull(type, "type");
        ensureNotNegative(expectedCount, "expectedCount");
        ensureSucceeded();
        List<ModelCall> calls = scopedInvocations().stream()
                .flatMap(invocation -> invocation.modelCalls().stream())
                .filter(type::isInstance)
                .toList();
        if (calls.size() != expectedCount) {
            throw failure("Expected %d %s, but there were %d: %s", expectedCount, describe(type), calls.size(), calls);
        }
        return this;
    }

    /**
     * Asserts that the invocation succeeded: it neither failed nor was cancelled. Other assertions check this too;
     * this one is for when nothing else is checked, for example for a streaming method whose failure the test would
     * otherwise not notice. Nested invocations are not checked: a failure of a nested invocation that the AI Service
     * handled (for example a tool that sent the error to the model) does not make this invocation fail.
     */
    public AiServiceInvocationAssert hasSucceeded() {
        isNotNull();
        if (actual.error() != null) {
            throw failure("Expected the invocation to succeed, but it failed with:%n%s", describeError(actual.error()));
        }
        if (actual.isCancelled()) {
            throw failure("Expected the invocation to succeed, but it was cancelled");
        }
        return this;
    }

    /**
     * Asserts that the invocation failed with an error whose message, or the message of one of its causes, contains
     * the given text (case-sensitive). The assertions that follow it check what happened before the failure, for
     * example which tools were executed.
     */
    public AiServiceInvocationAssert hasFailedWithMessageContaining(String text) {
        ensureNotBlank(text, "text");
        return assertFailed(
                "a message containing <%s>".formatted(text),
                error -> error.getMessage() != null && error.getMessage().contains(text));
    }

    /**
     * Asserts that the invocation failed with an error of the given type, or with an error caused by one. The
     * assertions that follow it check what happened before the failure, for example which tools were executed.
     */
    public AiServiceInvocationAssert hasFailedWith(Class<? extends Throwable> type) {
        ensureNotNull(type, "type");
        return assertFailed("<%s>".formatted(type.getName()), type::isInstance);
    }

    private AiServiceInvocationAssert assertFailed(String expected, Predicate<Throwable> matches) {
        isNotNull();
        if (actual.error() == null) {
            throw failure(
                    "Expected the invocation to fail with %s, but it did not fail%s",
                    expected, actual.isCancelled() ? " (it was cancelled)" : "");
        }
        if (Throwables.causalChain(actual.error()).stream().noneMatch(matches)) {
            throw failure(
                    "Expected the invocation to fail with %s, but it failed with:%n%s",
                    expected, describeError(actual.error()));
        }
        failureChecked = true;
        return this;
    }

    /**
     * Returns assertions on the names of all executed tools, in the order of execution, for example
     * {@code executedToolNames().containsExactly("getBookingDetails", "cancelBooking")}. Unlike the {@code hasTool...}
     * assertions, this includes the executions that failed: add {@link #hasNoFailedToolExecutions()} if they must all
     * have succeeded. After
     * {@link #includingNested()}, the names of the nested invocations come after those of this invocation.
     */
    public ListAssert<String> executedToolNames() {
        ensureSucceeded();
        return Assertions.assertThat(toolNames());
    }

    /**
     * Returns assertions on the tool executions, in the order of execution. After {@link #includingNested()}, the
     * executions of the nested invocations come after those of this invocation.
     *
     * @throws AssertionError if the AI Service did not report whether its tool executions failed (see
     *                        {@link AiServiceInvocation#toolExecutions()})
     */
    public ListAssert<ToolExecution> toolExecutions() {
        ensureSucceeded();
        ensureToolFailuresReported("Expected the tool executions");
        return Assertions.assertThat(
                scopedExecutions().stream().map(ScopedExecution::toolExecution).toList());
    }

    private void ensureSucceeded() {
        isNotNull();
        if (actual.error() != null && !failureChecked) {
            throw failure(
                    "Expected the invocation to succeed, but it failed with:%n%s%n"
                            + "To check an expected failure, call hasFailedWithMessageContaining(...) or hasFailedWith(...) first",
                    describeError(actual.error()));
        }
        if (actual.isCancelled()) {
            throw failure("Expected the invocation to succeed, but it was cancelled");
        }
    }

    private List<AiServiceInvocation> scopedInvocations() {
        if (actual.nestedInvocations().isEmpty() || scope == Scope.TOP_LEVEL_ONLY) {
            return List.of(actual);
        }
        if (scope == null) {
            throw failure(
                    "This invocation has %d nested invocations. Use includingNested() or topLevelOnly() to say which ones to check.",
                    withNested(actual).size() - 1);
        }
        return withNested(actual);
    }

    private static List<AiServiceInvocation> withNested(AiServiceInvocation root) {
        List<AiServiceInvocation> all = new ArrayList<>(List.of(root));
        for (int i = 0; i < all.size(); i++) {
            all.addAll(all.get(i).nestedInvocations());
        }
        return all;
    }

    private List<ScopedExecution> scopedExecutions() {
        List<ScopedExecution> executions = new ArrayList<>();
        for (AiServiceInvocation invocation : scopedInvocations()) {
            invocation
                    .allToolExecutions()
                    .forEach(toolExecution -> executions.add(new ScopedExecution(invocation, toolExecution)));
        }
        return executions;
    }

    private List<Content> scopedSources(String expectation) {
        List<AiServiceInvocation> reporting = scopedInvocations().stream()
                .filter(AiServiceInvocation::sourcesReported)
                .toList();
        if (reporting.isEmpty()) {
            throw failure(
                    "%s, but no sources were captured: the AI Service has no retrieval augmentor, or it does not report what it retrieves",
                    expectation);
        }
        return reporting.stream()
                .flatMap(invocation -> invocation.sources().stream())
                .toList();
    }

    private static String describe(Class<? extends ModelCall> type) {
        // for example "chat model call(s)" for ChatModelCall
        return type.getSimpleName().replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT) + "(s)";
    }

    private List<ChatModelCall> scopedOwnChatModelCalls() {
        scopedInvocations().stream()
                .filter(AiServiceInvocation::chatModelNotifiedNoListeners)
                .findFirst()
                .ifPresent(invocation -> {
                    throw failure(
                            "The calls of the AI Service to its chat model are not known: %s",
                            invocation.notNotifiedMessage());
                });
        return scopedInvocations().stream()
                .flatMap(invocation -> invocation.ownChatModelCalls().stream())
                .toList();
    }

    private void ensureToolFailuresReported(String expectation) {
        if (!scopedInvocations().stream().allMatch(AiServiceInvocation::toolFailuresReported)) {
            throw failure(
                    "%s, but the AI Service did not report whether its tool executions failed (see "
                            + "ToolExecutedEvent.isError()), so it is not known which ones succeeded. %s",
                    expectation, describeToolExecutions());
        }
    }

    private List<ScopedExecution> succeeded(String expectation) {
        ensureToolFailuresReported(expectation);
        return scopedExecutions().stream()
                .filter(execution -> !execution.toolExecution().hasFailed())
                .toList();
    }

    private long failedTimes(String toolName) {
        return scopedExecutions().stream()
                .filter(execution ->
                        execution.isOf(toolName) && execution.toolExecution().hasFailed())
                .count();
    }

    private List<String> toolNames() {
        return scopedExecutions().stream().map(ScopedExecution::toolName).toList();
    }

    private record ScopedExecution(AiServiceInvocation invocation, ToolExecution toolExecution) {

        String toolName() {
            return toolExecution.request().name();
        }

        boolean isOf(String toolName) {
            return toolName.equals(toolName());
        }

        void markVerified() {
            invocation.markVerified(toolExecution);
        }

        boolean isVerified() {
            return invocation.isVerified(toolExecution);
        }
    }

    private static String methodName(InvocationContext invocationContext) {
        String interfaceName = invocationContext.interfaceName();
        String simpleName = interfaceName == null
                ? "?"
                : interfaceName.substring(Math.max(interfaceName.lastIndexOf('.'), interfaceName.lastIndexOf('$')) + 1);
        return simpleName + "." + invocationContext.methodName();
    }

    /**
     * Returns {@code null} if the arguments are not a JSON object.
     */
    private static Map<String, Object> parseArguments(ToolExecution toolExecution) {
        try {
            return argumentsAsMap(toolExecution.request().arguments());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean containsAll(Map<?, ?> actualArguments, Map<?, ?> expectedArguments) {
        return expectedArguments.entrySet().stream()
                .allMatch(entry -> actualArguments.containsKey(entry.getKey())
                        && jsonEquals(actualArguments.get(entry.getKey()), entry.getValue()));
    }

    private static BigDecimal asNumber(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
        return null;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
            return Boolean.valueOf(text);
        }
        return null;
    }

    private static boolean jsonEquals(Object actualValue, Object expectedValue) {
        if (actualValue instanceof Number || expectedValue instanceof Number) {
            BigDecimal actualNumber = asNumber(actualValue);
            BigDecimal expectedNumber = asNumber(expectedValue);
            return actualNumber != null && expectedNumber != null && actualNumber.compareTo(expectedNumber) == 0;
        }
        if (actualValue instanceof Boolean || expectedValue instanceof Boolean) {
            Boolean actualBoolean = asBoolean(actualValue);
            return actualBoolean != null && actualBoolean.equals(asBoolean(expectedValue));
        }
        if (actualValue instanceof Map<?, ?> actualMap && expectedValue instanceof Map<?, ?> expectedMap) {
            return actualMap.size() == expectedMap.size() && containsAll(actualMap, expectedMap);
        }
        if (actualValue instanceof List<?> actualList && expectedValue instanceof List<?> expectedList) {
            if (actualList.size() != expectedList.size()) {
                return false;
            }
            for (int i = 0; i < actualList.size(); i++) {
                if (!jsonEquals(actualList.get(i), expectedList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(actualValue, expectedValue);
    }

    private static String describeError(Throwable error) {
        List<Throwable> chain = Throwables.causalChain(error);
        StringBuilder description = new StringBuilder("  " + error);
        for (Throwable cause : chain.subList(1, chain.size())) {
            description.append(System.lineSeparator()).append("  caused by: ").append(cause);
        }
        return description.toString();
    }

    private static String describeSources(List<Content> sources) {
        return sources.stream()
                .map(source -> "  - "
                        + truncate(
                                source.textSegment() == null
                                        ? null
                                        : source.textSegment().text()))
                .collect(joining(System.lineSeparator()));
    }

    private String describeToolExecutions() {
        if (scope == Scope.INCLUDING_NESTED) {
            return describeExecutionsOf(scopedExecutions());
        }
        List<ScopedExecution> own = actual.allToolExecutions().stream()
                .map(toolExecution -> new ScopedExecution(actual, toolExecution))
                .toList();
        return describeExecutionsOf(own) + describeNestedToolExecutions();
    }

    private static String describeExecutionsOf(List<ScopedExecution> executions) {
        if (executions.isEmpty()) {
            return "No tools were executed.";
        }
        return "Executed tools:" + System.lineSeparator() + describe(executions);
    }

    private static String describe(List<ScopedExecution> executions) {
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < executions.size(); i++) {
            ScopedExecution execution = executions.get(i);
            ToolExecution toolExecution = execution.toolExecution();
            String outcome = !execution.invocation().toolFailuresReported()
                    ? " (failure not reported)"
                    : toolExecution.hasFailed() ? " (FAILED)" : "";
            if (parseArguments(toolExecution) == null) {
                outcome += " (arguments could not be parsed as JSON)";
            }
            if (i > 0) {
                description.append(System.lineSeparator());
            }
            description.append("  %d. %s(%s) -> %s%s"
                    .formatted(
                            i + 1,
                            execution.toolName(),
                            truncate(toolExecution.request().arguments()),
                            truncate(toolExecution.result()),
                            outcome));
        }
        return description.toString();
    }

    private String describeNestedToolExecutions() {
        List<String> nestedToolNames = withNested(actual).stream()
                .skip(1)
                .flatMap(invocation -> invocation.allToolExecutions().stream())
                .map(toolExecution -> toolExecution.request().name())
                .toList();
        if (nestedToolNames.isEmpty()) {
            return "";
        }
        return "%nNested invocations (see nestedInvocations()) executed these tools, which are not included: %s"
                .formatted(nestedToolNames);
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "...";
    }
}
