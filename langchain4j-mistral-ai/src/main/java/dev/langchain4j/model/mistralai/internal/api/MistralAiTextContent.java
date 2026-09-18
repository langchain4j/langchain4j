package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MistralAiTextContent extends MistralAiMessageContent {

    public String text;

    @JsonCreator
    public MistralAiTextContent(@JsonProperty("text") String text) {
        super("text");
        this.text = text;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    static MistralAiTextContent fromText(String text) {
        return new MistralAiTextContent(text);
    }

    public String asText() {
        return text;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiTextContent that = (MistralAiTextContent) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text);
    }

    @Override
    public String toString() {
        return "MistralAiTextContent{" + "text='" + text + '\'' + ", type='" + type + '\'' + '}';
    }
}
