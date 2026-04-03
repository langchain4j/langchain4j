package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * Response object from the Anthropic token counting API.
 * <p>
 * Contains the estimated input token count for a given message.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MessageTokenCountResponse {

    /**
     * The number of input tokens in the message.
     */
    public Integer inputTokens;

    /**
     * Gets the input token count.
     *
     * @return the number of input tokens
     */
    public Integer getInputTokens() {
        return inputTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputTokens);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MessageTokenCountResponse)) return false;
        MessageTokenCountResponse that = (MessageTokenCountResponse) obj;
        return Objects.equals(inputTokens, that.inputTokens);
    }

    @Override
    public String toString() {
        return "MessageTokenCountResponse{" + "inputTokens=" + inputTokens + '}';
    }
}
