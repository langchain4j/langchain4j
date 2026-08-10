package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * Request parameters enabling Anthropic's (beta) cache diagnostics feature.
 * <p>
 * {@code previousMessageId} must be serialized even when {@code null}, since sending
 * {@code "diagnostics": {"previous_message_id": null}} is how a caller opts in on the first
 * turn of a conversation. Requires the {@code cache-diagnosis-2026-04-07} beta header.
 */
@JsonInclude(ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicDiagnosticsParameters {

    public String previousMessageId;

    public AnthropicDiagnosticsParameters() {}

    public AnthropicDiagnosticsParameters(String previousMessageId) {
        this.previousMessageId = previousMessageId;
    }

    public String getPreviousMessageId() {
        return previousMessageId;
    }

    public void setPreviousMessageId(String previousMessageId) {
        this.previousMessageId = previousMessageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnthropicDiagnosticsParameters)) return false;
        AnthropicDiagnosticsParameters that = (AnthropicDiagnosticsParameters) o;
        return Objects.equals(previousMessageId, that.previousMessageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(previousMessageId);
    }

    @Override
    public String toString() {
        return "AnthropicDiagnosticsParameters{" + "previousMessageId='" + previousMessageId + '\'' + '}';
    }
}
