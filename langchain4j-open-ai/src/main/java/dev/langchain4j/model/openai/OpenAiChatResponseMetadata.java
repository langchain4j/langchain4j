package dev.langchain4j.model.openai;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

@Experimental
public class OpenAiChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String serviceTier;
    private final String systemFingerprint;
    private final SuccessfulHttpResponse rawResponse; // TODO names
    private final List<ServerSentEvent> rawEvents; // TODO names

    private OpenAiChatResponseMetadata(Builder builder) {
        super(builder);
        this.created = builder.created;
        this.serviceTier = builder.serviceTier;
        this.systemFingerprint = builder.systemFingerprint;
        this.rawResponse = builder.rawResponse;
        this.rawEvents = copyIfNotNull(builder.rawEvents);
    }

    @Override
    public OpenAiTokenUsage tokenUsage() {
        return (OpenAiTokenUsage) super.tokenUsage();
    }

    public Long created() {
        return created;
    }

    public String serviceTier() {
        return serviceTier;
    }

    public String systemFingerprint() {
        return systemFingerprint;
    }

    public SuccessfulHttpResponse rawResponse() {
        return rawResponse;
    }

    public List<ServerSentEvent> rawEvents() {
        return rawEvents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiChatResponseMetadata that = (OpenAiChatResponseMetadata) o;
        return Objects.equals(created, that.created)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(systemFingerprint, that.systemFingerprint)
                && Objects.equals(rawResponse, that.rawResponse)
                && Objects.equals(rawEvents, that.rawEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                created,
                serviceTier,
                systemFingerprint,
                rawResponse,
                rawEvents
        );
    }

    @Override
    public String toString() {
        return "OpenAiChatResponseMetadata{" +
                "id='" + id() + '\'' +
                ", modelName='" + modelName() + '\'' +
                ", tokenUsage=" + tokenUsage() +
                ", finishReason=" + finishReason() +
                ", created=" + created +
                ", serviceTier='" + serviceTier + '\'' +
                ", systemFingerprint='" + systemFingerprint + '\'' +
                ", rawResponse=" + rawResponse +
                ", rawEvents=" + rawEvents +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long created;
        private String serviceTier;
        private String systemFingerprint;
        private SuccessfulHttpResponse rawResponse;
        private List<ServerSentEvent> rawEvents;

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public Builder systemFingerprint(String systemFingerprint) {
            this.systemFingerprint = systemFingerprint;
            return this;
        }

        public Builder rawResponse(SuccessfulHttpResponse rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        public Builder rawEvents(List<ServerSentEvent> rawEvents) {
            this.rawEvents = rawEvents;
            return this;
        }

        @Override
        public OpenAiChatResponseMetadata build() {
            return new OpenAiChatResponseMetadata(this);
        }
    }
}
