package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The result of the validation of a {@link ToolOutputGuardrail}.
 * <p>
 *     A successful result may carry a rewrite of the tool result, in one of two forms:
 * </p>
 * <ul>
 *     <li>a text rewrite ({@link #successWith(String)}): the text contents are replaced by the given text, the
 *     other contents and the error flag are kept;</li>
 *     <li>a contents rewrite ({@link #successWith(List, boolean)}): the contents and the error flag are both
 *     replaced.</li>
 * </ul>
 *
 * @since 1.24.0
 */
@Experimental
public final class ToolOutputGuardrailResult implements GuardrailResult<ToolOutputGuardrailResult> {
    private static final ToolOutputGuardrailResult SUCCESS = new ToolOutputGuardrailResult();

    private final Result result;
    private final String successfulText;
    private final List<Content> successfulContents;
    private final boolean successfulIsError;
    private final List<Failure> failures;

    private ToolOutputGuardrailResult(
            Result result,
            String successfulText,
            List<Content> successfulContents,
            boolean successfulIsError,
            List<Failure> failures) {
        this.result = ensureNotNull(result, "result");
        this.successfulText = successfulText;
        this.successfulContents = successfulContents;
        this.successfulIsError = successfulIsError;
        this.failures = Optional.ofNullable(failures).orElseGet(List::of);
    }

    private ToolOutputGuardrailResult() {
        this(Result.SUCCESS, null, null, false, Collections.emptyList());
    }

    ToolOutputGuardrailResult(List<Failure> failures, boolean fatal) {
        this(fatal ? Result.FATAL : Result.FAILURE, null, null, false, failures);
    }

    ToolOutputGuardrailResult(Failure failure, boolean fatal) {
        this(new ArrayList<>(List.of(failure)), fatal);
    }

    /**
     * Gets a successful tool output guardrail result.
     *
     * @return a successful result without any rewrite
     */
    public static ToolOutputGuardrailResult success() {
        return SUCCESS;
    }

    /**
     * Produces a successful result with rewritten text. The text contents of the tool result are replaced by a
     * single text content with the given text; any non-text contents and the error flag are kept.
     *
     * @param rewrittenText
     *            the new text of the tool result. When {@code null}, the result is a plain
     *            {@linkplain #success() success}.
     * @return a successful result carrying the rewritten text
     */
    public static ToolOutputGuardrailResult successWith(String rewrittenText) {
        return (rewrittenText == null)
                ? success()
                : new ToolOutputGuardrailResult(
                        Result.SUCCESS_WITH_RESULT, rewrittenText, null, false, Collections.emptyList());
    }

    /**
     * Produces a successful result that replaces all the contents of the tool result and its error flag.
     * <p>
     *     Passing {@code isError == true} turns the tool result into an error result, which counts as a failed
     *     step for compensation purposes.
     * </p>
     *
     * @param rewrittenContents
     *            the new contents of the tool result, must not be {@code null} and must not contain {@code null}
     * @param isError
     *            whether the rewritten tool result is an error
     * @return a successful result carrying the rewritten contents and error flag
     */
    public static ToolOutputGuardrailResult successWith(List<Content> rewrittenContents, boolean isError) {
        ensureNotNull(rewrittenContents, "rewrittenContents");
        rewrittenContents.forEach(content -> ensureNotNull(content, "rewrittenContents element"));
        var contents = List.copyOf(rewrittenContents);
        return new ToolOutputGuardrailResult(
                Result.SUCCESS_WITH_RESULT, joinText(contents), contents, isError, Collections.emptyList());
    }

    @Override
    public Result result() {
        return result;
    }

    /**
     * @return the rewritten text of the tool result: the given text for a text rewrite, the text contents joined
     *         with {@code \n} for a contents rewrite (an empty string if there are none), or {@code null} when
     *         the result was not rewritten
     */
    @Override
    public String successfulText() {
        return successfulText;
    }

    /**
     * Whether this result rewrites the contents and the error flag ({@link #successWith(List, boolean)}), as opposed
     * to the text only ({@link #successWith(String)}).
     *
     * @return true for a contents rewrite
     */
    public boolean hasRewrittenContents() {
        return successfulContents != null;
    }

    /**
     * Gets the contents of the tool result after applying this result to the given request.
     *
     * @param request
     *            the request this result was produced for
     * @return the rewritten contents, or the contents of the request if this result carries no rewrite
     */
    public List<Content> resultContents(ToolOutputGuardrailRequest request) {
        ensureNotNull(request, "request");
        List<Content> contents;

        if (successfulContents != null) {
            contents = successfulContents;
        } else if (hasRewrittenResult()) {
            contents = request.withText(successfulText).resultContents();
        } else {
            contents = request.resultContents();
        }

        return contents;
    }

    /**
     * Gets the error flag of the tool result after applying this result to the given request.
     *
     * @param request
     *            the request this result was produced for
     * @return the rewritten error flag for a contents rewrite, otherwise the error flag of the request
     */
    public boolean isError(ToolOutputGuardrailRequest request) {
        ensureNotNull(request, "request");
        return (successfulContents != null) ? successfulIsError : request.isError();
    }

    /**
     * Gets the request that results from applying this result to the given one. Used to chain guardrails.
     *
     * @param request
     *            the request this result was produced for
     * @return the request carrying the rewritten contents and error flag, or the same request if this result
     *         carries no rewrite
     */
    ToolOutputGuardrailRequest apply(ToolOutputGuardrailRequest request) {
        return hasRewrittenResult() ? request.withContents(resultContents(request), isError(request)) : request;
    }

    /**
     * Composes this result, which a guardrail returned after the one given, into the net result of both, relative
     * to the request the chain started with. Both results must be successful.
     * <ul>
     *     <li>no rewrite here: the earlier rewrite, if any, is kept;</li>
     *     <li>a contents rewrite here: it replaces the earlier rewrite;</li>
     *     <li>a text rewrite here: it replaces the text of an earlier contents rewrite, keeping its other contents
     *     and error flag, and it replaces an earlier text rewrite.</li>
     * </ul>
     */
    ToolOutputGuardrailResult composedAfter(ToolOutputGuardrailResult earlier) {
        ToolOutputGuardrailResult composed = this;

        if (!hasRewrittenResult()) {
            composed = earlier.hasRewrittenResult() ? earlier : this;
        } else if (!hasRewrittenContents() && earlier.hasRewrittenContents()) {
            composed = successWith(
                    ToolOutputGuardrailRequest.replaceText(earlier.successfulContents, successfulText),
                    earlier.successfulIsError);
        }

        return composed;
    }

    private static String joinText(List<Content> contents) {
        return contents.stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Gets the text of the error tool result that reports the failures of this result to the LLM: the messages of
     * all the failures, joined with {@code \n} in chain order. No framework text is added.
     *
     * @return the joined failure messages, or an empty string if there are no failures
     */
    public String failureMessage() {
        return failures.stream().map(Failure::message).collect(Collectors.joining("\n"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <F extends GuardrailResult.Failure> List<F> failures() {
        return (List<F>) failures;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return asString();
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolOutputGuardrailResult that = (ToolOutputGuardrailResult) o;
        return result == that.result
                && successfulIsError == that.successfulIsError
                && Objects.equals(successfulText, that.successfulText)
                && Objects.equals(successfulContents, that.successfulContents)
                && Objects.equals(failures, that.failures);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        return Objects.hash(result, successfulText, successfulContents, successfulIsError, failures);
    }

    /**
     * Represents a tool output guardrail failure.
     * Its message is sent to the LLM verbatim as the content of an error tool result, unless the failure is fatal.
     *
     * @since 1.24.0
     */
    @Experimental
    public static final class Failure implements GuardrailResult.Failure {
        private final String message;
        private final Throwable cause;
        private final Class<? extends Guardrail> guardrailClass;

        Failure(String message, Throwable cause, Class<? extends Guardrail> guardrailClass) {
            this.message = ensureNotNull(message, "message");
            this.cause = cause;
            this.guardrailClass = guardrailClass;
        }

        Failure(String message) {
            this(message, null, null);
        }

        Failure(String message, Throwable cause) {
            this(message, cause, null);
        }

        @Override
        public Failure withGuardrailClass(Class<? extends Guardrail> guardrailClass) {
            ensureNotNull(guardrailClass, "guardrailClass");
            return new Failure(this.message, this.cause, guardrailClass);
        }

        @Override
        public String message() {
            return message;
        }

        @Override
        public Throwable cause() {
            return cause;
        }

        @Override
        public Class<? extends Guardrail> guardrailClass() {
            return guardrailClass;
        }

        @Override
        @JacocoIgnoreCoverageGenerated
        public String toString() {
            return asString();
        }
    }
}
