package dev.langchain4j.model.watsonx;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;

public class WatsonxChatResponseMetadata extends ChatResponseMetadata {

    private final String object;
    private final String model;
    private final Long created;
    private final String modelVersion;
    private final String createdAt;

    private WatsonxChatResponseMetadata(Builder builder) {
        super(builder);
        object = builder.object;
        model = builder.model;
        created = builder.created;
        modelVersion = builder.modelVersion;
        createdAt = builder.createdAt;
    }

    public String getObject() {
        return object;
    }

    public String getModel() {
        return model;
    }

    public Long getCreated() {
        return created;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {
        private String object;
        private String model;
        private Long created;
        private String modelVersion;
        private String createdAt;

        public Builder object(String object) {
            this.object = object;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder created(Long created) {
            this.created = created;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public Builder createdAt(String createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        @Override
        public ChatResponseMetadata build() {
            return new WatsonxChatResponseMetadata(this);
        }
    }
}
