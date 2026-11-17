package dev.langchain4j.model.googleai;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(NON_NULL)
class GeminiDecisionRequest {

    private @Nullable String model;
    private @Nullable Object input;
    private @Nullable Map<String, Question> questions;
    private @Nullable Map<String, Object> customParameters;

    @JsonProperty("model")
    public @Nullable String getModel() {
        return model;
    }

    public void setModel(@Nullable String model) {
        this.model = model;
    }

    @JsonProperty("input")
    public @Nullable Object getInput() {
        return input;
    }

    public void setInput(@Nullable Object input) {
        this.input = input;
    }

    @JsonProperty("questions")
    public @Nullable Map<String, Question> getQuestions() {
        return questions;
    }

    public void setQuestions(@Nullable Map<String, Question> questions) {
        this.questions = questions;
    }

    @JsonAnyGetter
    public @Nullable Map<String, Object> getCustomParameters() {
        return customParameters;
    }

    public void setCustomParameters(@Nullable Map<String, Object> customParameters) {
        this.customParameters = customParameters;
    }

    @JsonInclude(NON_NULL)
    record Question(
            @JsonProperty("type") String type,
            @JsonProperty("instructions") String instructions,
            @JsonProperty("criteria") @Nullable Object criteria) {}

    @JsonInclude(NON_NULL)
    record Block(
            @JsonProperty("type") String type,
            @JsonProperty("text") @Nullable String text,
            @JsonProperty("data") @Nullable String data,
            @JsonProperty("uri") @Nullable String uri,
            @JsonProperty("mime_type") @Nullable String mimeType,
            @JsonProperty("resolution") @Nullable String resolution) {

        static Block text(String text) {
            return new Block("text", text, null, null, null, null);
        }
    }
}
