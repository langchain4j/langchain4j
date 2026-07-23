package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Response body for the list endpoint of the Mistral Batch API ({@code GET /v1/batch/jobs}).
 *
 * <p>Uses page-based pagination: {@link #total} is the number of jobs matching the query across all
 * pages, which {@code MistralAiBatchChatModel} combines with the requested page and page size to
 * decide whether a next page exists.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiBatchJobsResponse {

    public List<MistralAiBatchJob> data;
    public Integer total;
}
