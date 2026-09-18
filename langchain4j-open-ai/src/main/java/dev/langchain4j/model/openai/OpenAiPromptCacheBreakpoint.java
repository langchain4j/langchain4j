package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.util.HashMap;
import java.util.Map;

/**
 * Marks the end of a cacheable prompt prefix for {@code gpt-5.6} and later, which match a cache entry
 * exactly at a breakpoint instead of falling back to a shorter unmarked prefix.
 * <p>
 * Use {@link #mark(ChatMessage)} to mark a message. Since prompt caching is prefix-based, the
 * {@code prompt_cache_breakpoint} is applied to the <b>last content block</b> of the marked message,
 * so that everything up to and including that message forms the cached prefix:
 * <pre>{@code
 * SystemMessage systemMessage = OpenAiPromptCacheBreakpoint.mark(SystemMessage.from(SHARED_INSTRUCTIONS));
 * }</pre>
 * Marking is also possible by hand, by putting {@link #MODE_EXPLICIT} under the {@link #ATTRIBUTE_KEY}
 * attribute:
 * <pre>{@code
 * SystemMessage systemMessage = SystemMessage.builder()
 *         .text(SHARED_INSTRUCTIONS)
 *         .attributes(Map.of(OpenAiPromptCacheBreakpoint.ATTRIBUTE_KEY,
 *                            OpenAiPromptCacheBreakpoint.MODE_EXPLICIT))
 *         .build();
 * }</pre>
 * Breakpoints can be placed on a {@link SystemMessage}, a {@link UserMessage} and a
 * {@link ToolExecutionResultMessage}. {@link AiMessage} cannot carry one, because assistant output
 * blocks are not among the block types OpenAI accepts a breakpoint on.
 * <p>
 * Each request supports up to four cache writes, one of which is consumed by
 * {@link OpenAiPromptCacheOptions#MODE_IMPLICIT}.
 *
 * @see OpenAiPromptCacheOptions
 * @see <a href="https://developers.openai.com/api/docs/guides/prompt-caching">Prompt caching</a>
 * @since 1.21.0
 */
public class OpenAiPromptCacheBreakpoint {

    /**
     * The {@link ChatMessage} attribute key under which the breakpoint mode is stored.
     * Do not change, it is part of the public API.
     */
    public static final String ATTRIBUTE_KEY = "prompt_cache_breakpoint";

    /**
     * The only breakpoint mode currently accepted by OpenAI.
     */
    public static final String MODE_EXPLICIT = "explicit";

    private OpenAiPromptCacheBreakpoint() {}

    /**
     * Returns a copy of the given message marked as a prompt cache breakpoint, so that everything up to
     * and including that message forms the cached prefix:
     * <pre>{@code
     * SystemMessage systemMessage = OpenAiPromptCacheBreakpoint.mark(SystemMessage.from(SHARED_INSTRUCTIONS));
     * }</pre>
     * The given message is left untouched, and any other attributes it carries are preserved.
     *
     * @param message a {@link SystemMessage}, a {@link UserMessage} or a {@link ToolExecutionResultMessage}.
     * @return a marked copy of the given message.
     * @throws UnsupportedFeatureException if the message cannot carry a breakpoint, e.g. an {@link AiMessage}.
     * @since 1.21.0
     */
    @SuppressWarnings("unchecked")
    public static <T extends ChatMessage> T mark(T message) {
        ensureNotNull(message, "message");

        if (message instanceof SystemMessage systemMessage) {
            return (T) systemMessage
                    .toBuilder()
                    .attributes(marked(systemMessage.attributes()))
                    .build();
        }

        if (message instanceof UserMessage userMessage) {
            return (T) userMessage
                    .toBuilder()
                    .attributes(marked(userMessage.attributes()))
                    .build();
        }

        if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return (T) toolExecutionResultMessage
                    .toBuilder()
                    .attributes(marked(toolExecutionResultMessage.attributes()))
                    .build();
        }

        throw new UnsupportedFeatureException("OpenAI does not support a \"" + ATTRIBUTE_KEY + "\" on "
                + message.getClass().getSimpleName() + ". Mark a SystemMessage, a UserMessage "
                + "or a ToolExecutionResultMessage instead.");
    }

    private static Map<String, Object> marked(Map<String, Object> attributes) {
        Map<String, Object> marked = new HashMap<>(attributes);
        marked.put(ATTRIBUTE_KEY, MODE_EXPLICIT);
        return marked;
    }

    /**
     * Whether the given attributes mark a message as a prompt cache breakpoint.
     *
     * @param attributes the {@link ChatMessage} attributes, may be {@code null}.
     * @return {@code true} if a breakpoint should be emitted for this message.
     * @throws IllegalArgumentException if the attribute is present but holds an unsupported value.
     *                                 OpenAI answers such a request with an HTTP 400.
     */
    public static boolean isMarked(Map<String, Object> attributes) {
        if (attributes == null) {
            return false;
        }

        Object mode = attributes.get(ATTRIBUTE_KEY);
        if (mode == null) {
            return false;
        }

        if (!MODE_EXPLICIT.equals(mode)) {
            throw illegalArgument(
                    "Unsupported value for the \"%s\" attribute: %s. The only supported value is \"%s\".",
                    ATTRIBUTE_KEY, mode, MODE_EXPLICIT);
        }

        return true;
    }
}
