package dev.langchain4j.model.openaiofficial;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;

@Experimental
public class OpenAiOfficialResponsesChatResponseMetadata extends ChatResponseMetadata {

    private final Long createdAt;
    private final Long completedAt;
    private final String serviceTier;
    private final List<OpenAiOfficialServerToolResult> serverToolResults;

    private OpenAiOfficialResponsesChatResponseMetadata(Builder builder) {
        super(builder);
        this.createdAt = builder.createdAt;
        this.completedAt = builder.completedAt;
        this.serviceTier = builder.serviceTier;
        this.serverToolResults = copy(builder.serverToolResults);
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

    public List<OpenAiOfficialServerToolResult> serverToolResults() {
        return serverToolResults;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .createdAt(createdAt)
                .completedAt(completedAt)
                .serviceTier(serviceTier)
                .serverToolResults(serverToolResults);
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
                && Objects.equals(serverToolResults, that.serverToolResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), createdAt, completedAt, serviceTier, serverToolResults);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long createdAt;
        private Long completedAt;
        private String serviceTier;
        private List<OpenAiOfficialServerToolResult> serverToolResults;

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

        public Builder serverToolResults(List<OpenAiOfficialServerToolResult> serverToolResults) {
            this.serverToolResults = serverToolResults;
            return this;
        }

        @Override
        public OpenAiOfficialResponsesChatResponseMetadata build() {
            return new OpenAiOfficialResponsesChatResponseMetadata(this);
        }
    }
}
