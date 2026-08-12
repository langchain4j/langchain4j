package dev.langchain4j.model.anthropic;

import dev.langchain4j.Experimental;
import java.util.Objects;

/**
 * Result of Anthropic's (beta) cache diagnostics comparison for a single request, surfaced via
 * {@link AnthropicChatResponseMetadata#cacheDiagnostics()}.
 * <p>
 * Cache diagnostics compares the current request against the one identified by
 * {@code previousMessageId} (set via {@link AnthropicChatRequestParameters.Builder#previousMessageId(String)})
 * and reports the first point where the two diverged, so that an unexpected prompt-cache miss
 * (e.g. {@code usage.cacheReadInputTokens} dropping to zero) can be diagnosed instead of guessed at.
 * Requires {@link AnthropicChatRequestParameters.Builder#returnCacheDiagnostics(Boolean)} to be enabled.
 * <p>
 * {@link AnthropicChatResponseMetadata#cacheDiagnostics()} itself is {@code null} when diagnostics
 * were not requested, or when a comparison ran and found no divergence. When this object is present,
 * {@link #cacheMissReasonType()} is {@code null} while the comparison is still running (treat this as
 * inconclusive and check the next turn), otherwise it is one of: {@code "model_changed"},
 * {@code "system_changed"}, {@code "tools_changed"}, {@code "messages_changed"},
 * {@code "previous_message_not_found"}, or {@code "unavailable"}.
 * <p>
 * See the <a href="https://docs.anthropic.com/en/docs/build-with-claude/cache-diagnostics">cache diagnostics docs</a>.
 *
 * @since 1.10.0
 */
@Experimental
public class AnthropicCacheDiagnostics {

    private final String cacheMissReasonType;
    private final Integer cacheMissedInputTokens;

    private AnthropicCacheDiagnostics(Builder builder) {
        this.cacheMissReasonType = builder.cacheMissReasonType;
        this.cacheMissedInputTokens = builder.cacheMissedInputTokens;
    }

    /**
     * The cache-miss reason discriminator, or {@code null} while the comparison is still running.
     */
    public String cacheMissReasonType() {
        return cacheMissReasonType;
    }

    /**
     * An estimate of how many input tokens fell after the divergence point. Only populated for the
     * {@code "*_changed"} reason types; {@code null} otherwise.
     */
    public Integer cacheMissedInputTokens() {
        return cacheMissedInputTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnthropicCacheDiagnostics)) return false;
        AnthropicCacheDiagnostics that = (AnthropicCacheDiagnostics) o;
        return Objects.equals(cacheMissReasonType, that.cacheMissReasonType)
                && Objects.equals(cacheMissedInputTokens, that.cacheMissedInputTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheMissReasonType, cacheMissedInputTokens);
    }

    @Override
    public String toString() {
        return "AnthropicCacheDiagnostics{" + "cacheMissReasonType='"
                + cacheMissReasonType + '\'' + ", cacheMissedInputTokens="
                + cacheMissedInputTokens + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String cacheMissReasonType;
        private Integer cacheMissedInputTokens;

        public Builder cacheMissReasonType(String cacheMissReasonType) {
            this.cacheMissReasonType = cacheMissReasonType;
            return this;
        }

        public Builder cacheMissedInputTokens(Integer cacheMissedInputTokens) {
            this.cacheMissedInputTokens = cacheMissedInputTokens;
            return this;
        }

        public AnthropicCacheDiagnostics build() {
            return new AnthropicCacheDiagnostics(this);
        }
    }
}
