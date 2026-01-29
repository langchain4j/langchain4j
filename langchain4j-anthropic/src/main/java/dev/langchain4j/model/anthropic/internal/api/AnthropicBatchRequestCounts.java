package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Counts of requests in each processing state within a batch.
 *
 * @param processing number of requests still being processed
 * @param succeeded number of successfully completed requests
 * @param errored number of requests that encountered errors
 * @param canceled number of requests canceled before processing
 * @param expired number of requests that expired before processing
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AnthropicBatchRequestCounts(
        Integer processing, Integer succeeded, Integer errored, Integer canceled, Integer expired) {}
