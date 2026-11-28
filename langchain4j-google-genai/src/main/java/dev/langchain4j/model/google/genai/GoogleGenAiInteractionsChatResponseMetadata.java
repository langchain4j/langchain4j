package dev.langchain4j.model.google.genai;

import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionStatus;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

@Experimental
public class GoogleGenAiInteractionsChatResponseMetadata extends ChatResponseMetadata {

    private final Interaction rawResponse;
    private final InteractionStatus status;

    private GoogleGenAiInteractionsChatResponseMetadata(Builder builder) {
        super(builder);
        this.rawResponse = builder.rawResponse;
        this.status = builder.status;
    }

    public Interaction rawResponse() {
        return rawResponse;
    }

    public InteractionStatus status() {
        return status;
    }

    @Override
    public Builder toBuilder() {
        return builder()
                .id(id())
                .modelName(modelName())
                .tokenUsage(tokenUsage())
                .finishReason(finishReason())
                .rawResponse(rawResponse)
                .status(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GoogleGenAiInteractionsChatResponseMetadata that = (GoogleGenAiInteractionsChatResponseMetadata) o;
        return Objects.equals(rawResponse, that.rawResponse) && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rawResponse, status);
    }

    @Override
    public String toString() {
        return "GoogleGenAiInteractionsChatResponseMetadata{"
                + "id=" + id()
                + ", modelName=" + modelName()
                + ", tokenUsage=" + tokenUsage()
                + ", finishReason=" + finishReason()
                + ", status=" + status
                + ", rawResponse=" + rawResponse
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Interaction rawResponse;
        private InteractionStatus status;

        public Builder rawResponse(Interaction rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        public Builder status(InteractionStatus status) {
            this.status = status;
            return this;
        }

        @Override
        public GoogleGenAiInteractionsChatResponseMetadata build() {
            return new GoogleGenAiInteractionsChatResponseMetadata(this);
        }
    }
}
