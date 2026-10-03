package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

/**
 * Response object from the Anthropic Files List API ({@code GET /v1/files}).
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicFilesListResponse {

    /**
     * The list of file metadata objects.
     */
    public List<AnthropicFile> data;

    /**
     * The ID of the first file in the list (for pagination).
     */
    public String firstId;

    /**
     * The ID of the last file in the list (for pagination).
     */
    public String lastId;

    /**
     * Whether there are more files available beyond this page.
     */
    public Boolean hasMore;

    public AnthropicFilesListResponse() {}

    @Override
    public int hashCode() {
        return Objects.hash(data, firstId, lastId, hasMore);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicFilesListResponse)) return false;
        AnthropicFilesListResponse that = (AnthropicFilesListResponse) obj;
        return Objects.equals(data, that.data)
                && Objects.equals(firstId, that.firstId)
                && Objects.equals(lastId, that.lastId)
                && Objects.equals(hasMore, that.hasMore);
    }

    @Override
    public String toString() {
        return "AnthropicFilesListResponse{" + "data="
                + data + ", firstId='"
                + firstId + '\'' + ", lastId='"
                + lastId + '\'' + ", hasMore="
                + hasMore + '}';
    }
}
