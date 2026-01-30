package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiThinkingContent extends MistralAiMessageContent {

    private final List<MistralAiTextContent> thinking;

    @JsonCreator
    public MistralAiThinkingContent(@JsonProperty("thinking") List<MistralAiTextContent> thinking) {
        super("thinking");
        this.thinking = thinking;
    }

    public List<MistralAiTextContent> getThinking() {
        return thinking;
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
        return "MistralAiThinkingContent{" + "thinking=" + thinking + ", type='" + type + '\'' + '}';
    }
}
