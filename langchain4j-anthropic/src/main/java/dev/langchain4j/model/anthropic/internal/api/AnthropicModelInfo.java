package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * Represents information about an Anthropic model.
 * <p>
 * This class contains metadata about available models returned by the Models API.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicModelInfo {

    /**
     * The unique identifier for the model.
     */
    public String id;

    /**
     * The timestamp when the model was created.
     */
    public String createdAt;

    /**
     * The human-readable display name for the model.
     */
    public String displayName;

    /**
     * The type of the model.
     */
    public String type;

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt, displayName, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicModelInfo)) return false;
        AnthropicModelInfo that = (AnthropicModelInfo) obj;
        return Objects.equals(id, that.id)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(type, that.type);
    }

    @Override
    public String toString() {
        return "AnthropicModelInfo{" + "id='"
                + id + '\'' + ", createdAt='"
                + createdAt + '\'' + ", displayName='"
                + displayName + '\'' + ", type='"
                + type + '\'' + '}';
    }
}
