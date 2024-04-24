package dev.langchain4j.model.anthropic;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
public class AnthropicToolResultContent extends AnthropicMessageContent {

    public String toolUseId;
    public String content;
    public Boolean isError;

    public AnthropicToolResultContent(String toolUseId, String content, Boolean isError) {
        super("tool_result");
        this.toolUseId = toolUseId;
        this.content = content;
        this.isError = isError;
    }
}
