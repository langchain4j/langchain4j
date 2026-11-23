package dev.langchain4j.model.xai;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

public class XAiChatResponseMetadata extends ChatResponseMetadata {

    private final Long createdAt;
    private final SuccessfulHttpResponse rawHttpResponse;

    private XAiChatResponseMetadata(Builder builder) {
        super(builder);
        this.createdAt = builder.createdAt;
        this.rawHttpResponse = builder.rawHttpResponse;
    }

    public Long createdAt() {
        return createdAt;
    }

    public SuccessfulHttpResponse rawHttpResponse() {
        return rawHttpResponse;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder())).createdAt(createdAt).rawHttpResponse(rawHttpResponse);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        XAiChatResponseMetadata that = (XAiChatResponseMetadata) o;
        return Objects.equals(createdAt, that.createdAt) && Objects.equals(rawHttpResponse, that.rawHttpResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), createdAt, rawHttpResponse);
    }

    @Override
    public String toString() {
        return "XAiChatResponseMetadata{" + "id='"
                + id() + '\'' + ", modelName='"
                + modelName() + '\'' + ", tokenUsage="
                + tokenUsage() + ", finishReason="
                + finishReason() + ", createdAt="
                + createdAt + ", rawHttpResponse="
                + rawHttpResponse + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long createdAt;
        private SuccessfulHttpResponse rawHttpResponse;

        public Builder createdAt(Long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder rawHttpResponse(SuccessfulHttpResponse rawHttpResponse) {
            this.rawHttpResponse = rawHttpResponse;
            return this;
        }

        @Override
        public XAiChatResponseMetadata build() {
            return new XAiChatResponseMetadata(this);
        }
    }
}
