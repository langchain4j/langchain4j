package dev.langchain4j.guardrail.config;

import dev.langchain4j.Experimental;
import dev.langchain4j.spi.guardrail.config.ToolInputGuardrailsConfigBuilderFactory;
import java.util.ServiceLoader;

/**
 * Configuration specifically for tool input guardrails.
 * <p>
 *     It sets the <b>arguments rewrite note</b>: the chat memory keeps a tool call as the LLM made it, so when an input
 *     guardrail changed the value of an argument the LLM sent, the tool result that the LLM gets says so, in a note
 *     followed by the result of the tool. The values are compared parsed, so a rewrite that only changes the spacing
 *     or the order of the keys adds no note. Otherwise the LLM would read, on every later turn, that the call it made ran as it made it (a transfer
 *     of 10000 that a guardrail capped to 1000, for example). The note is in the text of the result, not in a separate
 *     content, and only the LLM gets it: the events and {@code toolExecutions()} keep the result of the tool as is.
 * </p>
 * <p>
 *     Frameworks that extend this library (like Quarkus or Spring) may provide their own implementations of this
 *     configuration.
 * </p>
 *
 * @since 1.25.0
 */
@Experimental
public interface ToolInputGuardrailsConfig extends GuardrailsConfig {

    /**
     * The placeholder of the template of the arguments rewrite note for the changed arguments, as a JSON object: the
     * top-level arguments the LLM sent whose values changed, with their new values (a removed one as {@code null}).
     * An argument that a guardrail added is never in it.
     */
    String ARGUMENTS_PLACEHOLDER = "{{arguments}}";

    /**
     * The placeholder of the template of the arguments rewrite note for the text of the result of the tool.
     */
    String RESULT_PLACEHOLDER = "{{result}}";

    /**
     * The default template of the arguments rewrite note: the note in its own tag, then the result of the tool.
     */
    String DEFAULT_ARGUMENTS_REWRITE_NOTE_TEMPLATE = "<tool_arguments_changed>The arguments of this call were changed "
            + "before it ran: " + ARGUMENTS_PLACEHOLDER + "</tool_arguments_changed>\n" + RESULT_PLACEHOLDER;

    /**
     * Whether the tool result that the LLM gets carries the arguments rewrite note when an input guardrail changed
     * the arguments of the call. Enabled by default.
     *
     * @return {@code true} if the note is added
     */
    default boolean isArgumentsRewriteNoteEnabled() {
        return true;
    }

    /**
     * The template of the text of a tool result whose call an input guardrail changed: {@value #ARGUMENTS_PLACEHOLDER}
     * is replaced with the changed arguments (see {@link #ARGUMENTS_PLACEHOLDER}), and {@value #RESULT_PLACEHOLDER}
     * with the text of the result of the tool. Both are replaced in one pass, so a placeholder in the arguments or in
     * the result is not expanded. Defaults to {@link #DEFAULT_ARGUMENTS_REWRITE_NOTE_TEMPLATE}.
     * <p>
     *     The note is stored in the chat memory. An argument a guardrail added (a tenant or a user id from the
     *     invocation, for example) is never in it, but a guardrail that overwrites an argument the LLM sent with a value
     *     the LLM must not see does show that value: leave {@value #ARGUMENTS_PLACEHOLDER} out of the template, or
     *     disable the note, for such a guardrail.
     * </p>
     *
     * @return the template, which contains {@value #RESULT_PLACEHOLDER}
     */
    default String argumentsRewriteNoteTemplate() {
        return DEFAULT_ARGUMENTS_REWRITE_NOTE_TEMPLATE;
    }

    /**
     * Gets a builder instance for building {@link ToolInputGuardrailsConfig} instances.
     * @return A {@link ToolInputGuardrailsConfigBuilder} for building {@link ToolInputGuardrailsConfig} instances.
     */
    static ToolInputGuardrailsConfigBuilder builder() {
        return ServiceLoader.load(ToolInputGuardrailsConfigBuilderFactory.class)
                .findFirst()
                .map(ToolInputGuardrailsConfigBuilderFactory::get)
                .orElseGet(DefaultToolInputGuardrailsConfig::builder);
    }

    /**
     * Builder for {@link ToolInputGuardrailsConfig} instances.
     * <p>
     *     This is needed so other frameworks (like Quarkus and Spring) can extend the configuration mechanism with their own
     *     implementations while also adhering to the interfaces and specs defined here.
     * </p>
     *
     * @since 1.25.0
     */
    @Experimental
    interface ToolInputGuardrailsConfigBuilder extends GuardrailsConfigBuilder<ToolInputGuardrailsConfig> {

        /**
         * Sets whether the tool result that the LLM gets carries the arguments rewrite note. Enabled by default.
         *
         * @param isEnabled whether the note is added
         * @return this builder
         * @see ToolInputGuardrailsConfig#isArgumentsRewriteNoteEnabled()
         */
        ToolInputGuardrailsConfigBuilder argumentsRewriteNoteEnabled(boolean isEnabled);

        /**
         * Sets the template of the arguments rewrite note.
         *
         * @param template the template; must contain {@value ToolInputGuardrailsConfig#RESULT_PLACEHOLDER}, so the
         *                 LLM still gets the result of the tool
         * @return this builder
         * @see ToolInputGuardrailsConfig#argumentsRewriteNoteTemplate()
         */
        ToolInputGuardrailsConfigBuilder argumentsRewriteNoteTemplate(String template);
    }
}
