package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Represents metadata for Anthropic API requests.
 * Currently supports user_id for tracking and abuse detection.
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicMetadata {

    /**
     * An external identifier for the user who is associated with the request.
     * This should be a uuid, hash value, or other opaque identifier.
     * Anthropic may use this id to help detect abuse.
     * Do not include any identifying information such as name, email address, or phone number.
     */
    public String userId;

    public AnthropicMetadata() {}

    public AnthropicMetadata(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().userId(this.userId);
    }

    public static class Builder {

        private String userId;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public AnthropicMetadata build() {
            return new AnthropicMetadata(userId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicMetadata that = (AnthropicMetadata) o;
        return java.util.Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "AnthropicMetadata{" + "userId='" + userId + '\'' + '}';
    }
}
