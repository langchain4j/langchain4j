package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Map;

/**
 * Marks the end of a cacheable prompt prefix for {@code gpt-5.6} and later, which match a cache entry
 * exactly at a breakpoint instead of falling back to a shorter unmarked prefix.
 * <p>
 * A message is marked by putting {@link #MODE_EXPLICIT} under the {@link #ATTRIBUTE_KEY} attribute.
 * Since prompt caching is prefix-based, the {@code prompt_cache_breakpoint} is applied to the
 * <b>last content block</b> of the marked message, so that everything up to and including that
 * message forms the cached prefix:
 * <pre>{@code
 * SystemMessage systemMessage = SystemMessage.builder()
 *         .text(SHARED_INSTRUCTIONS)
 *         .attributes(Map.of(OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY,
 *                            OpenAiOfficialPromptCacheBreakpoint.MODE_EXPLICIT))
 *         .build();
 * }</pre>
 * {@link SystemMessage} and {@link UserMessage} expose a mutable attribute map, so the marker can also
 * be set after construction:
 * <pre>{@code
 * SystemMessage systemMessage = SystemMessage.from(SHARED_INSTRUCTIONS);
 * systemMessage.attributes().put(OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY,
 *                                OpenAiOfficialPromptCacheBreakpoint.MODE_EXPLICIT);
 * }</pre>
 * Breakpoints can be placed on a {@link SystemMessage}, a {@link UserMessage} and a
 * {@link ToolExecutionResultMessage}. {@link AiMessage} cannot carry one, because assistant output
 * blocks are not among the block types OpenAI accepts a breakpoint on.
 * <p>
 * Each request supports up to four cache writes, one of which is consumed by
 * {@link OpenAiOfficialPromptCacheOptions#MODE_IMPLICIT}.
 *
 * @see OpenAiOfficialPromptCacheOptions
 * @see <a href="https://developers.openai.com/api/docs/guides/prompt-caching">Prompt caching</a>
 * @since 1.20.0
 */
public class OpenAiOfficialPromptCacheBreakpoint {

    /**
     * The {@link ChatMessage} attribute key under which the breakpoint mode is stored.
     * Do not change, it is part of the public API.
     */
    public static final String ATTRIBUTE_KEY = "prompt_cache_breakpoint";

    /**
     * The only breakpoint mode currently accepted by OpenAI.
     */
    public static final String MODE_EXPLICIT = "explicit";

    private OpenAiOfficialPromptCacheBreakpoint() {}

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
