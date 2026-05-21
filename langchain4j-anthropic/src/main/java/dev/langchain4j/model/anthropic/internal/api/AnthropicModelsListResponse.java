package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

/**
 * Response object from the Anthropic Models List API.
 * <p>
 * Contains a paginated list of available models and pagination metadata.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicModelsListResponse {

    /**
     * The list of model information objects.
     */
    public List<AnthropicModelInfo> data;

    /**
     * The ID of the first model in the list (for pagination).
     */
    public String firstId;

    /**
     * The ID of the last model in the list (for pagination).
     */
    public String lastId;

    /**
     * Whether there are more models available beyond this page.
     */
    public Boolean hasMore;

    @Override
    public int hashCode() {
        return Objects.hash(data, firstId, lastId, hasMore);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicModelsListResponse)) return false;
        AnthropicModelsListResponse that = (AnthropicModelsListResponse) obj;
        return Objects.equals(data, that.data)
                && Objects.equals(firstId, that.firstId)
                && Objects.equals(lastId, that.lastId)
                && Objects.equals(hasMore, that.hasMore);
    }

    @Override
    public String toString() {
        return "AnthropicModelsListResponse{" + "data="
                + data + ", firstId='"
                + firstId + '\'' + ", lastId='"
                + lastId + '\'' + ", hasMore="
                + hasMore + '}';
    }
}
