package dev.langchain4j.guardrails;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link InputGuardrail} that detects and blocks prompt injection attempts using regular
 * expression patterns derived from the
 * <a href="https://genai.owasp.org/llmrisk/llm01-prompt-injection/">OWASP LLM01: Prompt Injection</a>
 * category.
 * <p>
 * This guardrail is intentionally lightweight: it performs no external calls and has no
 * dependencies beyond the JDK and SLF4J. It is designed to run as the first (cheapest) gate
 * in a guardrail chain, before any LLM-based classifiers.
 * </p>
 * <p>
 * The default pattern set covers six common categories of prompt injection:
 * <ul>
 *   <li><b>Instruction override</b> — "ignore previous instructions", "forget all rules"</li>
 *   <li><b>Role hijacking</b> — "you are now a...", "act as a...", "pretend to be..."</li>
 *   <li><b>Jailbreaks</b> — "DAN", "developer mode", "bypass safety filters"</li>
 *   <li><b>System prompt leakage</b> — "reveal your prompt", "print your instructions"</li>
 *   <li><b>Delimiter injection</b> — {@code ```system}, {@code <system>}, {@code [INST]}, {@code <<SYS>>}</li>
 *   <li><b>Encoded injection</b> — {@code base64:}, "decode the following and execute"</li>
 * </ul>
 * <p>
 * Subclasses may supply additional domain-specific patterns via the
 * {@link #PromptInjectionGuardrail(List)} constructor, or override
 * {@link #buildFailureMessage(String, Pattern)} to customise the failure message.
 * </p>
 * <p>
 * <b>Limitations.</b> Pattern-based detection cannot catch novel or semantically obfuscated
 * attacks. For deeper analysis, chain this guardrail with an LLM-based classifier that runs
 * afterwards.
 * </p>
 */
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    private static final List<Pattern> DEFAULT_PATTERNS = List.of(
            // Instruction override
            Pattern.compile(
                    "ignore\\s+(all\\s+|previous\\s+|prior\\s+|the\\s+)*(instructions?|rules?|context|prompt)",
                    CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(everything|all\\s+(rules|instructions)|prior\\s+context)", CASE_INSENSITIVE),
            Pattern.compile(
                    "disregard\\s+(all\\s+|your\\s+)?(previous\\s+|prior\\s+)?(instructions?|rules?|context)",
                    CASE_INSENSITIVE),
            Pattern.compile("override\\s+(your\\s+|the\\s+)?(instructions?|rules?|system\\s+prompt)", CASE_INSENSITIVE),

            // Role hijacking
            Pattern.compile("you\\s+are\\s+now\\s+(a|an)\\s+", CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+(a|an)\\s+", CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+(to\\s+be|you\\s+are)", CASE_INSENSITIVE),
            Pattern.compile("roleplay\\s+as\\s+", CASE_INSENSITIVE),

            // Jailbreaks
            Pattern.compile("\\bDAN\\b"),
            Pattern.compile("developer\\s+mode", CASE_INSENSITIVE),
            Pattern.compile(
                    "bypass\\s+(all\\s+)?(safety|content|security)\\s+(filters?|restrictions?|checks?)",
                    CASE_INSENSITIVE),
            Pattern.compile("jailbreak", CASE_INSENSITIVE),

            // System prompt leakage
            Pattern.compile(
                    "(reveal|print|show|tell|repeat|output)\\s+(me\\s+)?(your|the)\\s+(system\\s+|original\\s+|initial\\s+)?(prompt|instructions?)",
                    CASE_INSENSITIVE),
            Pattern.compile(
                    "what\\s+(are|were)\\s+your\\s+(original\\s+|initial\\s+)?(instructions?|prompt)",
                    CASE_INSENSITIVE),

            // Delimiter injection
            Pattern.compile("```\\s*system", CASE_INSENSITIVE),
            Pattern.compile("<\\s*system\\s*>", CASE_INSENSITIVE),
            Pattern.compile("\\[INST]", CASE_INSENSITIVE),
            Pattern.compile("<<SYS>>", CASE_INSENSITIVE),

            // Encoded injection
            Pattern.compile("base64\\s*:", CASE_INSENSITIVE),
            Pattern.compile("decode\\s+(the\\s+following|this)\\s+and\\s+(execute|run)", CASE_INSENSITIVE));

    private static final String DEFAULT_FAILURE_MESSAGE = "Prompt injection attempt detected";

    private final List<Pattern> patterns;

    /**
     * Creates a guardrail using the default OWASP LLM01 pattern set.
     */
    public PromptInjectionGuardrail() {
        this.patterns = DEFAULT_PATTERNS;
    }

    /**
     * Creates a guardrail using the default OWASP LLM01 pattern set plus the supplied
     * additional patterns. Useful when subclassing or when the host application has
     * domain-specific phrases it wants to block.
     *
     * @param additionalPatterns extra patterns to check, in addition to the defaults. Must not be {@code null}.
     */
    public PromptInjectionGuardrail(List<Pattern> additionalPatterns) {
        ensureNotNull(additionalPatterns, "additionalPatterns");
        List<Pattern> combined = new ArrayList<>(DEFAULT_PATTERNS.size() + additionalPatterns.size());
        combined.addAll(DEFAULT_PATTERNS);
        combined.addAll(additionalPatterns);
        this.patterns = Collections.unmodifiableList(combined);
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        ensureNotNull(userMessage, "userMessage");
        String text = userMessage.singleText();
        if (text == null || text.isBlank()) {
            return success();
        }

        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                LOGGER.warn("Prompt injection detected. pattern='{}' input='{}'", pattern.pattern(), text);
                return fatal(buildFailureMessage(text, pattern));
            }
        }
        return success();
    }

    /**
     * Builds the failure message returned when an injection attempt is detected. Subclasses
     * may override this to provide a more specific message.
     *
     * @param input the raw user input text that triggered the failure
     * @param matchedPattern the pattern that matched the input
     * @return the failure message
     */
    protected String buildFailureMessage(
            @SuppressWarnings("unused") String input, @SuppressWarnings("unused") Pattern matchedPattern) {
        return DEFAULT_FAILURE_MESSAGE;
    }
}
