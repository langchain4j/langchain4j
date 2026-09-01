package dev.langchain4j.model.openai.internal.chat;

import static dev.langchain4j.model.openai.internal.chat.Role.TOOL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = ToolMessage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ToolMessage implements Message {

    @JsonProperty
    private final Role role = TOOL;

    @JsonProperty
    private final String toolCallId;

    @JsonProperty
    private final Object content;

    public ToolMessage(Builder builder) {
        this.toolCallId = builder.toolCallId;
        this.content = builder.stringContent != null ? builder.stringContent : builder.contents;
    }

    public Role role() {
        return role;
    }

    public String toolCallId() {
        return toolCallId;
    }

    /**
     * Returns the content when it was set as a plain string, {@code null} when it was set as a list of
     * content blocks (see {@link #contents()}).
     */
    public String content() {
        return content instanceof String stringContent ? stringContent : null;
    }

    /**
     * Returns the content when it was set as a list of content blocks, {@code null} when it was set as a
     * plain string (see {@link #content()}). The list form is required to attach a
     * {@code prompt_cache_breakpoint} to the message.
     */
    @SuppressWarnings("unchecked")
    public List<Content> contents() {
        return content instanceof List<?> contents ? (List<Content>) contents : null;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolMessage && equalTo((ToolMessage) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(ToolMessage another) {
        return Objects.equals(role, another.role)
                && Objects.equals(toolCallId, another.toolCallId)
                && Objects.equals(content, another.content);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(role);
        h += (h << 5) + Objects.hashCode(toolCallId);
        h += (h << 5) + Objects.hashCode(content);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "ToolMessage{" + "role=" + role + ", toolCallId=" + toolCallId + ", content=" + content + "}";
    }

    public static ToolMessage from(String toolCallId, String content) {
        return ToolMessage.builder().toolCallId(toolCallId).content(content).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String toolCallId;
        private String stringContent;
        private List<Content> contents;

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder content(String content) {
            this.stringContent = content;
            return this;
        }

        public Builder content(List<Content> content) {
            this.contents = content;
            return this;
        }

        public ToolMessage build() {
            return new ToolMessage(this);
        }
    }
}
