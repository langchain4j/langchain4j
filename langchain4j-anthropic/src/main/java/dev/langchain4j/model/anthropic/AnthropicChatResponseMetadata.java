package dev.langchain4j.model.anthropic;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;

/**
 * @since 1.10.0
 */
public class AnthropicChatResponseMetadata extends ChatResponseMetadata {

    private final SuccessfulHttpResponse rawHttpResponse;
    private final List<ServerSentEvent> rawServerSentEvents;

    private AnthropicChatResponseMetadata(Builder builder) {
        super(builder);
        this.rawHttpResponse = builder.rawHttpResponse;
        this.rawServerSentEvents = copy(builder.rawServerSentEvents);
    }

    @Override
    public AnthropicTokenUsage tokenUsage() {
        return (AnthropicTokenUsage) super.tokenUsage();
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
                .rawHttpResponse(rawHttpResponse)
                .rawServerSentEvents(rawServerSentEvents);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicChatResponseMetadata that = (AnthropicChatResponseMetadata) o;
        return Objects.equals(rawHttpResponse, that.rawHttpResponse)
                && Objects.equals(rawServerSentEvents, that.rawServerSentEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rawHttpResponse, rawServerSentEvents);
    }

    @Override
    public String toString() {
        return "AnthropicChatResponseMetadata{" + "id='"
                + id() + '\'' + ", modelName='"
                + modelName() + '\'' + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", created="
                + rawHttpResponse + ", rawServerSentEvents="
                + rawServerSentEvents + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private SuccessfulHttpResponse rawHttpResponse;
        private List<ServerSentEvent> rawServerSentEvents;

        public Builder rawHttpResponse(SuccessfulHttpResponse rawHttpResponse) {
            this.rawHttpResponse = rawHttpResponse;
            return this;
        }

        public Builder rawServerSentEvents(List<ServerSentEvent> rawServerSentEvents) {
            this.rawServerSentEvents = rawServerSentEvents;
            return this;
        }

        @Override
        public AnthropicChatResponseMetadata build() {
            return new AnthropicChatResponseMetadata(this);
        }
    }
}
