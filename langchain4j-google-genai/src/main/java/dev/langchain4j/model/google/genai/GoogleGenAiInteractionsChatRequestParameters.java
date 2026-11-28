package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import com.google.genai.gaos.models.interactions.SafetySetting;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Experimental
public class GoogleGenAiInteractionsChatRequestParameters extends DefaultChatRequestParameters {

    public static final GoogleGenAiInteractionsChatRequestParameters EMPTY =
            GoogleGenAiInteractionsChatRequestParameters.builder().build();

    private final String thinkingLevel;
    private final Boolean includeThoughts;
    private final String serviceTier;
    private final Map<String, String> labels;
    private final List<SafetySetting> safetySettings;
    private final Boolean automaticContinuation;

    private GoogleGenAiInteractionsChatRequestParameters(Builder builder) {
        super(builder);
        this.thinkingLevel = builder.thinkingLevel;
        this.includeThoughts = builder.includeThoughts;
        this.serviceTier = builder.serviceTier;
        this.labels = copyIfNotNull(builder.labels);
        this.safetySettings = copyIfNotNull(builder.safetySettings);
        this.automaticContinuation = builder.automaticContinuation;
    }

    public String thinkingLevel() {
        return thinkingLevel;
    }

    public Boolean includeThoughts() {
        return includeThoughts;
    }

    public String serviceTier() {
        return serviceTier;
    }

    public Map<String, String> labels() {
        return labels;
    }

    public List<SafetySetting> safetySettings() {
        return safetySettings;
    }

    public Boolean automaticContinuation() {
        return automaticContinuation;
    }

    @Override
    public GoogleGenAiInteractionsChatRequestParameters overrideWith(ChatRequestParameters that) {
        return GoogleGenAiInteractionsChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public GoogleGenAiInteractionsChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return GoogleGenAiInteractionsChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GoogleGenAiInteractionsChatRequestParameters that = (GoogleGenAiInteractionsChatRequestParameters) o;
        return Objects.equals(thinkingLevel, that.thinkingLevel)
                && Objects.equals(includeThoughts, that.includeThoughts)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(labels, that.labels)
                && Objects.equals(safetySettings, that.safetySettings)
                && Objects.equals(automaticContinuation, that.automaticContinuation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                thinkingLevel,
                includeThoughts,
                serviceTier,
                labels,
                safetySettings,
                automaticContinuation);
    }

    @Override
    public String toString() {
        return "GoogleGenAiInteractionsChatRequestParameters{" + "modelName="
                + quoted(modelName()) + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", thinkingLevel="
                + quoted(thinkingLevel) + ", includeThoughts="
                + includeThoughts + ", serviceTier="
                + quoted(serviceTier) + ", labels="
                + labels + ", safetySettings="
                + safetySettings + ", automaticContinuation="
                + automaticContinuation + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String thinkingLevel;
        private Boolean includeThoughts;
        private String serviceTier;
        private Map<String, String> labels;
        private List<SafetySetting> safetySettings;
        private Boolean automaticContinuation;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof GoogleGenAiInteractionsChatRequestParameters interactionsParameters) {
                thinkingLevel(getOrDefault(interactionsParameters.thinkingLevel(), thinkingLevel));
                includeThoughts(getOrDefault(interactionsParameters.includeThoughts(), includeThoughts));
                serviceTier(getOrDefault(interactionsParameters.serviceTier(), serviceTier));
                labels(getOrDefault(interactionsParameters.labels(), labels));
                safetySettings(getOrDefault(interactionsParameters.safetySettings(), safetySettings));
                automaticContinuation(
                        getOrDefault(interactionsParameters.automaticContinuation(), automaticContinuation));
            }
            return this;
        }

        public Builder thinkingLevel(String thinkingLevel) {
            this.thinkingLevel = thinkingLevel;
            return this;
        }

        public Builder includeThoughts(Boolean includeThoughts) {
            this.includeThoughts = includeThoughts;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder safetySettings(List<SafetySetting> safetySettings) {
            this.safetySettings = safetySettings;
            return this;
        }

        public Builder automaticContinuation(Boolean automaticContinuation) {
            this.automaticContinuation = automaticContinuation;
            return this;
        }

        @Override
        public GoogleGenAiInteractionsChatRequestParameters build() {
            return new GoogleGenAiInteractionsChatRequestParameters(this);
        }
    }
}
