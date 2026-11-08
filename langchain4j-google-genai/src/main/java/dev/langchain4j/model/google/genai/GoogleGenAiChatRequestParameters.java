package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Map;
import java.util.Objects;

public class GoogleGenAiChatRequestParameters extends DefaultChatRequestParameters {

    public static final GoogleGenAiChatRequestParameters EMPTY =
            GoogleGenAiChatRequestParameters.builder().build();

    private final String cachedContent;
    private final Map<String, String> labels;
    private final Boolean automaticContinuation;

    private GoogleGenAiChatRequestParameters(Builder builder) {
        super(builder);
        this.cachedContent = builder.cachedContent;
        this.labels = copyIfNotNull(builder.labels);
        this.automaticContinuation = builder.automaticContinuation;
    }

    /**
     * The resource name of a previously created cache (e.g. {@code "cachedContents/abc123"}) to attach
     * to this specific request, overriding any global {@code cachedContent} configured on the
     * {@link GoogleGenAiChatModel}/{@link GoogleGenAiStreamingChatModel}.
     */
    public String cachedContent() {
        return cachedContent;
    }

    /**
     * Custom key-value labels attached to this specific request for billing, metrics, or tracking,
     * overriding any model-level {@code labels}.
     */
    public Map<String, String> labels() {
        return labels;
    }

    /**
     * Controls whether the Google GenAI SDK automatically continues generation when a response stops
     * with an intermediate {@code CONTINUATION} finish reason and continuation token.
     */
    public Boolean automaticContinuation() {
        return automaticContinuation;
    }

    @Override
    public GoogleGenAiChatRequestParameters overrideWith(ChatRequestParameters that) {
        return GoogleGenAiChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    @Override
    public GoogleGenAiChatRequestParameters defaultedBy(ChatRequestParameters that) {
        return GoogleGenAiChatRequestParameters.builder()
                .overrideWith(that)
                .overrideWith(this)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GoogleGenAiChatRequestParameters that = (GoogleGenAiChatRequestParameters) o;
        return Objects.equals(cachedContent, that.cachedContent)
                && Objects.equals(labels, that.labels)
                && Objects.equals(automaticContinuation, that.automaticContinuation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), cachedContent, labels, automaticContinuation);
    }

    @Override
    public String toString() {
        return "GoogleGenAiChatRequestParameters{" + "modelName="
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
                + responseFormat() + ", cachedContent="
                + quoted(cachedContent) + ", labels="
                + labels + ", automaticContinuation="
                + automaticContinuation + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {

        private String cachedContent;
        private Map<String, String> labels;
        private Boolean automaticContinuation;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof GoogleGenAiChatRequestParameters googleGenAiParameters) {
                cachedContent(getOrDefault(googleGenAiParameters.cachedContent(), cachedContent));
                labels(getOrDefault(googleGenAiParameters.labels(), labels));
                automaticContinuation(
                        getOrDefault(googleGenAiParameters.automaticContinuation(), automaticContinuation));
            }
            return this;
        }

        public Builder cachedContent(String cachedContent) {
            this.cachedContent = cachedContent;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder automaticContinuation(Boolean automaticContinuation) {
            this.automaticContinuation = automaticContinuation;
            return this;
        }

        @Override
        public GoogleGenAiChatRequestParameters build() {
            return new GoogleGenAiChatRequestParameters(this);
        }
    }
}
