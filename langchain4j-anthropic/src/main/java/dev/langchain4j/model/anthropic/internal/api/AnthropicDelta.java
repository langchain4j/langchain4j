package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicDelta {

    // when AnthropicStreamingData.type = "content_block_delta"
    public String type;
    public String text;
    public String partialJson;
    public String thinking;
    public String signature;
    public String data;

    // when AnthropicStreamingData.type = "message_delta"
    public String stopReason;
    public String stopSequence;
}
