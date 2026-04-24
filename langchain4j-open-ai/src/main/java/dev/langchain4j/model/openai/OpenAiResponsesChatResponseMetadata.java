package dev.langchain4j.model.openai;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;

public class OpenAiResponsesChatResponseMetadata extends ChatResponseMetadata {

    private final Long createdAt;
    private final Long completedAt;
    private final String serviceTier;
    private final SuccessfulHttpResponse rawHttpResponse;
    private final List<ServerSentEvent> rawServerSentEvents;

    private OpenAiResponsesChatResponseMetadata(Builder builder) {
        super(builder);
        this.createdAt = builder.createdAt;
        this.completedAt = builder.completedAt;
        this.serviceTier = builder.serviceTier;
        this.rawHttpResponse = builder.rawHttpResponse;
        this.rawServerSentEvents = copy(builder.rawServerSentEvents);
    }

    @Override
    public OpenAiTokenUsage tokenUsage() {
        TokenUsage base = super.tokenUsage();
        if (base == null) {
            return null;
        }
        if (base instanceof OpenAiTokenUsage openAiTokenUsage) {
            return openAiTokenUsage;
        }
        return OpenAiTokenUsage.builder()
                .inputTokenCount(base.inputTokenCount())
                .outputTokenCount(base.outputTokenCount())
                .totalTokenCount(base.totalTokenCount())
                .build();
    }

    public Long createdAt() {
        return createdAt;
    }

    public Long completedAt() {
        return completedAt;
    }

    public String serviceTier() {
        return serviceTier;
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
                .createdAt(createdAt)
                .completedAt(completedAt)
                .serviceTier(serviceTier)
                .rawHttpResponse(rawHttpResponse)
                .rawServerSentEvents(rawServerSentEvents);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiResponsesChatResponseMetadata that = (OpenAiResponsesChatResponseMetadata) o;
        return Objects.equals(createdAt, that.createdAt)
                && Objects.equals(completedAt, that.completedAt)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(rawHttpResponse, that.rawHttpResponse)
                && Objects.equals(rawServerSentEvents, that.rawServerSentEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                createdAt,
                completedAt,
                serviceTier,
                rawHttpResponse,
                rawServerSentEvents);
    }

    @Override
    public String toString() {
        return "OpenAiResponsesChatResponseMetadata{" + "id='"
                + id() + '\'' + ", modelName='"
                + modelName() + '\'' + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", createdAt="
                + createdAt + ", completedAt="
                + completedAt + ", serviceTier='"
                + serviceTier + '\'' + ", rawHttpResponse="
                + rawHttpResponse + ", rawServerSentEvents="
                + rawServerSentEvents + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long createdAt;
        private Long completedAt;
        private String serviceTier;
        private SuccessfulHttpResponse rawHttpResponse;
        private List<ServerSentEvent> rawServerSentEvents;

        public Builder createdAt(Long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder completedAt(Long completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
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
        public OpenAiResponsesChatResponseMetadata build() {
            return new OpenAiResponsesChatResponseMetadata(this);
        }
    }
}
