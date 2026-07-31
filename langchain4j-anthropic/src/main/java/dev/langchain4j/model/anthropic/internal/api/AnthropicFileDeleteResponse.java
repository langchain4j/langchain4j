package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * Response object from the Anthropic Files Delete API ({@code DELETE /v1/files/{file_id}}).
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicFileDeleteResponse {

    /**
     * The ID of the deleted file.
     */
    public String id;

    /**
     * The object type, always {@code "file_deleted"}.
     */
    public String type;

    public AnthropicFileDeleteResponse() {}

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicFileDeleteResponse)) return false;
        AnthropicFileDeleteResponse that = (AnthropicFileDeleteResponse) obj;
        return Objects.equals(id, that.id) && Objects.equals(type, that.type);
    }

    @Override
    public String toString() {
        return "AnthropicFileDeleteResponse{" + "id='" + id + '\'' + ", type='" + type + '\'' + '}';
    }
}
