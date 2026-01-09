package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Response from listing Message Batches.
 *
 * @param data list of batch responses
 * @param hasMore true if there are more batches available
 * @param firstId ID of the first batch in this page
 * @param lastId ID of the last batch in this page (use as after_id for next page)
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AnthropicBatchListResponse(
        List<AnthropicBatchResponse> data, Boolean hasMore, String firstId, String lastId) {}
