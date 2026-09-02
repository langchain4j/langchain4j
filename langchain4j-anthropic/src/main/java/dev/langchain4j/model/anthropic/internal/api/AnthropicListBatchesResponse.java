package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Response body for the list endpoint of the Anthropic Message Batches API
 * ({@code GET /v1/messages/batches}).
 *
 * <p>Uses id-cursor pagination: when {@link #hasMore} is {@code true}, {@link #lastId} is the
 * cursor to pass back as {@code after_id} to fetch the next page.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicListBatchesResponse {

    public List<AnthropicBatch> data;
    public boolean hasMore;
    public String lastId;
}
