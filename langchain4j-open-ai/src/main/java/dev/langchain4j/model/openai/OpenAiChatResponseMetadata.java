package dev.langchain4j.model.openai;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;

public class OpenAiChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String serviceTier;
    private final String systemFingerprint;
    private final SuccessfulHttpResponse rawHttpResponse;
    private final List<ServerSentEvent> rawServerSentEvents;

    private OpenAiChatResponseMetadata(Builder builder) {
        super(builder);
        this.created = builder.created;
        this.serviceTier = builder.serviceTier;
        this.systemFingerprint = builder.systemFingerprint;
        this.rawHttpResponse = builder.rawHttpResponse;
        this.rawServerSentEvents = copy(builder.rawServerSentEvents);
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

    public SuccessfulHttpResponse rawHttpResponse() {
        return rawHttpResponse;
    }

    public List<ServerSentEvent> rawServerSentEvents() {
        return rawServerSentEvents;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .created(created)
                .serviceTier(serviceTier)
                .systemFingerprint(systemFingerprint)
                .rawHttpResponse(rawHttpResponse)
                .rawServerSentEvents(rawServerSentEvents);
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
                && Objects.equals(rawHttpResponse, that.rawHttpResponse)
                && Objects.equals(rawServerSentEvents, that.rawServerSentEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                created,
                serviceTier,
                systemFingerprint,
                rawHttpResponse,
                rawServerSentEvents
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
                ", rawHttpResponse=" + rawHttpResponse +
                ", rawServerSentEvents=" + rawServerSentEvents +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long created;
        private String serviceTier;
        private String systemFingerprint;
        private SuccessfulHttpResponse rawHttpResponse;
        private List<ServerSentEvent> rawServerSentEvents;

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

        public Builder rawHttpResponse(SuccessfulHttpResponse rawHttpResponse) {
            this.rawHttpResponse = rawHttpResponse;
            return this;
        }

        public Builder rawServerSentEvents(List<ServerSentEvent> rawServerSentEvents) {
            this.rawServerSentEvents = rawServerSentEvents;
            return this;
        }

        @Override
        public OpenAiChatResponseMetadata build() {
            return new OpenAiChatResponseMetadata(this);
        }
    }
}
