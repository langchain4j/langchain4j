package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * Result of an Anthropic (beta) cache-diagnostics comparison, returned on
 * {@code AnthropicCreateMessageResponse#diagnostics} (and, for streaming, on the {@code message_start} event)
 * when the {@code cache-diagnosis-2026-04-07} beta header and {@code diagnostics.previous_message_id}
 * request parameter were used.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicDiagnostics {

    /**
     * {@code null} when either {@code previous_message_id} was {@code null} (first turn) or the comparison
     * found no divergence, and also {@code null} (with this object itself present) while the comparison
     * was still running when the response was serialized.
     */
    public AnthropicCacheMissReason cacheMissReason;

    public AnthropicDiagnostics() {}

    public AnthropicCacheMissReason getCacheMissReason() {
        return cacheMissReason;
    }

    public void setCacheMissReason(AnthropicCacheMissReason cacheMissReason) {
        this.cacheMissReason = cacheMissReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnthropicDiagnostics)) return false;
        AnthropicDiagnostics that = (AnthropicDiagnostics) o;
        return Objects.equals(cacheMissReason, that.cacheMissReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheMissReason);
    }

    @Override
    public String toString() {
        return "AnthropicDiagnostics{" + "cacheMissReason=" + cacheMissReason + '}';
    }
}
