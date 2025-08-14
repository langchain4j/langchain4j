package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicToolResultContent that = (AnthropicToolResultContent) o;
        return Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(content, that.content)
                && Objects.equals(isError, that.isError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), toolUseId, content, isError);
    }

    @Override
    public String toString() {
        return "AnthropicToolResultContent{" + "isError="
                + isError + ", type='"
                + type + '\'' + ", cacheControl="
                + cacheControl + '}';
    }
}
