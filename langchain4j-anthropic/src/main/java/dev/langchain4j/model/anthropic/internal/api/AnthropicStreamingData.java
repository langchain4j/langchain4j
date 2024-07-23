package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicStreamingData {

    public String type;

    // when type = "message_start"
    public AnthropicResponseMessage message;

    // when type = "content_block_start" || "content_block_delta" || "content_block_stop"
    public Integer index;

    // when type = "content_block_start"
    public AnthropicContent contentBlock;

    // when type = "content_block_delta" || "message_delta"
    public AnthropicDelta delta; // mix of Content and Message

    // when type = "message_delta"
    public AnthropicUsage usage;
}