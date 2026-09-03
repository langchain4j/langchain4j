package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.internal.Utils.quoted;

import java.util.Objects;

/**
 * Represents the {@code prompt_cache_options} request object supported by {@code gpt-5.6} and later.
 * <p>
 * On these models a cached prefix is matched exactly at a
 * {@link OpenAiOfficialPromptCacheBreakpoint breakpoint}, without falling back to a shorter unmarked prefix.
 * The {@code mode} controls where breakpoints come from:
 * <ul>
 *     <li>{@link #MODE_IMPLICIT} - OpenAI places a breakpoint at the end of the newest eligible message.</li>
 *     <li>{@link #MODE_EXPLICIT} - only the breakpoints set via
 *     {@link OpenAiOfficialPromptCacheBreakpoint} are used. Without any breakpoint, nothing is cached.</li>
 * </ul>
 * <p>
 * Note that {@code prompt_cache_options.ttl} supersedes {@code prompt_cache_retention}, which applies to
 * earlier models only. Setting both on the same request is rejected by OpenAI.
 *
 * @see <a href="https://developers.openai.com/api/docs/guides/prompt-caching">Prompt caching</a>
 * @since 1.20.0
 */
public class OpenAiOfficialPromptCacheOptions {

    /**
     * OpenAI places a breakpoint at the end of the newest eligible message.
     */
    public static final String MODE_IMPLICIT = "implicit";

    /**
     * Only explicitly set breakpoints are used.
     *
     * @see OpenAiOfficialPromptCacheBreakpoint
     */
    public static final String MODE_EXPLICIT = "explicit";

    /**
     * The only TTL currently accepted by OpenAI. It is also the default.
     */
    public static final String TTL_30M = "30m";

    private final String mode;
    private final String ttl;

    private OpenAiOfficialPromptCacheOptions(Builder builder) {
        this.mode = builder.mode;
        this.ttl = builder.ttl;
    }

    /**
     * Returns {@code prompt_cache_options.mode}.
     */
    public String mode() {
        return mode;
    }

    /**
     * Returns {@code prompt_cache_options.ttl}.
     */
    public String ttl() {
        return ttl;
    }

    /**
     * Creates options with {@link #MODE_IMPLICIT}.
     */
    public static OpenAiOfficialPromptCacheOptions implicit() {
        return builder().mode(MODE_IMPLICIT).build();
    }

    /**
     * Creates options with {@link #MODE_EXPLICIT}.
     */
    public static OpenAiOfficialPromptCacheOptions explicit() {
        return builder().mode(MODE_EXPLICIT).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenAiOfficialPromptCacheOptions that = (OpenAiOfficialPromptCacheOptions) o;
        return Objects.equals(mode, that.mode) && Objects.equals(ttl, that.ttl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, ttl);
    }

    @Override
    public String toString() {
        return "OpenAiOfficialPromptCacheOptions{" + "mode=" + quoted(mode) + ", ttl=" + quoted(ttl) + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String mode;
        private String ttl;

        /**
         * @param mode {@link #MODE_IMPLICIT} or {@link #MODE_EXPLICIT}.
         */
        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        /**
         * @param ttl how long the cache entry lives, e.g. {@link #TTL_30M}.
         */
        public Builder ttl(String ttl) {
            this.ttl = ttl;
            return this;
        }

        public OpenAiOfficialPromptCacheOptions build() {
            return new OpenAiOfficialPromptCacheOptions(this);
        }
    }
}
