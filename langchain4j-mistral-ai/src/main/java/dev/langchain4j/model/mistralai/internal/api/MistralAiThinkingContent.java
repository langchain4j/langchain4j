package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MistralAiThinkingContent extends MistralAiMessageContent {

    List<MistralAiMessageContent> thinking;

    protected MistralAiThinkingContent() {
        super("thinking");
    }

    public MistralAiThinkingContent(List<MistralAiMessageContent> thinking) {
        super("thinking");
        this.thinking = thinking;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiThinkingContent that = (MistralAiThinkingContent) o;
        return Objects.equals(thinking, that.thinking);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), thinking);
    }

    @Override
    public String toString() {
        return "MistralAiThinkingContent{" + "thinking="
                + thinking + ", type='"
                + type + '\'' + '}';
    }
}
