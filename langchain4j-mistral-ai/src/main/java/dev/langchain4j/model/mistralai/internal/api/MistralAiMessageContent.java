package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = MistralAiTextContent.class, name = "text"),
    @JsonSubTypes.Type(value = MistralAiThinkingContent.class, name = "thinking")
})
public abstract class MistralAiMessageContent {

    public String type;

    public MistralAiMessageContent(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MistralAiMessageContent that = (MistralAiMessageContent) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    public String asText() {
        throw new UnsupportedOperationException("Cannot convert message content of type '" + type + "' to text");
    }
}
