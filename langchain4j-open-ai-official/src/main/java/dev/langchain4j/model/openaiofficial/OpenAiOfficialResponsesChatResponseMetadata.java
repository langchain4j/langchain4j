package dev.langchain4j.model.openaiofficial;

import com.openai.models.responses.Response;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.Objects;

@Experimental
public class OpenAiOfficialResponsesChatResponseMetadata extends ChatResponseMetadata {

    private final Long createdAt;
    private final Long completedAt;
    private final String serviceTier;
    private final Response rawResponse;

    private OpenAiOfficialResponsesChatResponseMetadata(Builder builder) {
        super(builder);
        this.createdAt = builder.createdAt;
        this.completedAt = builder.completedAt;
        this.serviceTier = builder.serviceTier;
        this.rawResponse = builder.rawResponse;
    }

    @Override
    public OpenAiOfficialTokenUsage tokenUsage() {
        return (OpenAiOfficialTokenUsage) super.tokenUsage();
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

    public Response rawResponse() {
        return rawResponse;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .createdAt(createdAt)
                .completedAt(completedAt)
                .serviceTier(serviceTier)
                .rawResponse(rawResponse);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OpenAiOfficialResponsesChatResponseMetadata that = (OpenAiOfficialResponsesChatResponseMetadata) o;
        return Objects.equals(createdAt, that.createdAt)
                && Objects.equals(completedAt, that.completedAt)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(rawResponse, that.rawResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), createdAt, completedAt, serviceTier, rawResponse);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long createdAt;
        private Long completedAt;
        private String serviceTier;
        private Response rawResponse;

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

        public Builder rawResponse(Response rawResponse) {
            this.rawResponse = rawResponse;
            return this;
        }

        @Override
        public OpenAiOfficialResponsesChatResponseMetadata build() {
            return new OpenAiOfficialResponsesChatResponseMetadata(this);
        }
    }
}
