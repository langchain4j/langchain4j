package dev.langchain4j.model.openai.responses;

import com.openai.models.responses.ResponseOutputItem;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata for Responses API chat responses.
 * Includes Responses API specific fields like reasoning items and encrypted content.
 */
public final class ResponsesChatResponseMetadata extends ChatResponseMetadata {

    private final Long created;
    private final String serviceTier;
    private final String systemFingerprint;
    private final List<Map<String, Object>> reasoningItems;
    private final List<ResponseOutputItem> outputItems;

    private ResponsesChatResponseMetadata(Builder builder) {
        super(builder);
        this.created = builder.created;
        this.serviceTier = builder.serviceTier;
        this.systemFingerprint = builder.systemFingerprint;
        this.reasoningItems = builder.reasoningItems != null
                ? Collections.unmodifiableList(builder.reasoningItems)
                : Collections.emptyList();
        this.outputItems = builder.outputItems != null
                ? Collections.unmodifiableList(builder.outputItems)
                : Collections.emptyList();
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

    public List<Map<String, Object>> reasoningItems() {
        return reasoningItems;
    }

    /**
     * Gets the raw output items from the response.
     * These items should be passed back in the next request for encrypted reasoning chaining.
     */
    public List<ResponseOutputItem> outputItems() {
        return outputItems;
    }

    /**
     * Checks if this response contains encrypted reasoning content.
     */
    public boolean hasEncryptedReasoning() {
        return reasoningItems.stream()
                .anyMatch(item -> item.containsKey("encrypted_content"));
    }

    /**
     * Gets the encrypted reasoning content for stateless mode chaining.
     */
    public String getEncryptedReasoningContent() {
        return reasoningItems.stream()
                .filter(item -> item.containsKey("encrypted_content"))
                .findFirst()
                .map(item -> (String) item.get("encrypted_content"))
                .orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ResponsesChatResponseMetadata that = (ResponsesChatResponseMetadata) o;
        return Objects.equals(created, that.created)
                && Objects.equals(serviceTier, that.serviceTier)
                && Objects.equals(systemFingerprint, that.systemFingerprint)
                && Objects.equals(reasoningItems, that.reasoningItems)
                && Objects.equals(outputItems, that.outputItems);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), created, serviceTier, systemFingerprint, reasoningItems, outputItems);
    }

    @Override
    public String toString() {
        return "ResponsesChatResponseMetadata{" +
                "id='" + id() + '\'' +
                ", modelName='" + modelName() + '\'' +
                ", tokenUsage=" + tokenUsage() +
                ", finishReason=" + finishReason() +
                ", created=" + created +
                ", serviceTier='" + serviceTier + '\'' +
                ", systemFingerprint='" + systemFingerprint + '\'' +
                ", reasoningItems=" + reasoningItems +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private Long created;
        private String serviceTier;
        private String systemFingerprint;
        private List<Map<String, Object>> reasoningItems;
        private List<ResponseOutputItem> outputItems;

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

        public Builder reasoningItems(List<Map<String, Object>> reasoningItems) {
            this.reasoningItems = reasoningItems;
            return this;
        }

        public Builder outputItems(List<ResponseOutputItem> outputItems) {
            this.outputItems = outputItems;
            return this;
        }

        @Override
        public ResponsesChatResponseMetadata build() {
            return new ResponsesChatResponseMetadata(this);
        }
    }
}
