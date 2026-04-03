package dev.langchain4j.model.watsonx;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;

public class WatsonxChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String modelVersion;

    private WatsonxChatResponseMetadata(Builder builder) {
        super(builder);
        created = builder.created;
        modelVersion = builder.modelVersion;
    }

    public Long getCreated() {
        return created;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {
        private Long created;
        private String modelVersion;

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        @Override
        public ChatResponseMetadata build() {
            return new WatsonxChatResponseMetadata(this);
        }
    }
}
