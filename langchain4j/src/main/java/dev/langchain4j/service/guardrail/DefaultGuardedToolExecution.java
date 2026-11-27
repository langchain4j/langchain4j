package dev.langchain4j.service.guardrail;

import static dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig.ARGUMENTS_PLACEHOLDER;
import static dev.langchain4j.guardrail.config.ToolInputGuardrailsConfig.RESULT_PLACEHOLDER;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @param argumentsRewriteNote the note that the arguments were changed, present when an input guardrail changed the
 *                             value of an argument of the LLM and the tool ran with it
 */
record DefaultGuardedToolExecution(
        ToolExecutionRequest request,
        ToolExecutionResult result,
        Optional<ToolGuardrailStage> failedStage,
        Optional<ArgumentsRewriteNote> argumentsRewriteNote)
        implements GuardedToolExecution {

    private static final Pattern PLACEHOLDERS = Pattern.compile(Pattern.quote(ARGUMENTS_PLACEHOLDER) + "|"
            + Pattern.quote(RESULT_PLACEHOLDER));

    DefaultGuardedToolExecution {
        ensureNotNull(request, "request");
        ensureNotNull(result, "result");
        ensureNotNull(failedStage, "failedStage");
        ensureNotNull(argumentsRewriteNote, "argumentsRewriteNote");
    }

    DefaultGuardedToolExecution(GuardedToolExecution.Builder builder) {
        this(
                builder.request,
                builder.result,
                Optional.ofNullable(builder.failedStage),
                Optional.ofNullable(builder.argumentsRewriteNoteTemplate)
                        .map(template -> new ArgumentsRewriteNote(template, builder.changedArguments)));
    }

    @Override
    public String resultTextForLlm(String resultText) {
        ensureNotNull(resultText, "resultText");
        return argumentsRewriteNote
                .map(note -> fill(note.template(), note.changedArguments(), resultText))
                .orElse(resultText);
    }

    /**
     * Replaces the placeholders of the template in one pass, so a placeholder in the arguments or in the result is
     * kept as it is.
     */
    private static String fill(String template, String arguments, String resultText) {
        return PLACEHOLDERS
                .matcher(template)
                .replaceAll(match -> Matcher.quoteReplacement(
                        match.group().equals(ARGUMENTS_PLACEHOLDER) ? arguments : resultText));
    }

    /**
     * @param template         the template of the note
     * @param changedArguments the arguments of the LLM whose values changed, with their new values, as a JSON object
     */
    record ArgumentsRewriteNote(String template, String changedArguments) {

        ArgumentsRewriteNote {
            ensureNotNull(template, "template");
            ensureNotNull(changedArguments, "changedArguments");
        }
    }
}
