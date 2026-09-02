package dev.langchain4j.model.openai.internal.chat;

import static dev.langchain4j.model.openai.internal.chat.Role.SYSTEM;

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

@JsonDeserialize(builder = SystemMessage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class SystemMessage implements Message {

    @JsonProperty
    private final Role role = SYSTEM;

    private final String content;

    private final List<Content> contents;

    @JsonProperty
    private final String name;

    public SystemMessage(Builder builder) {
        this.content = builder.stringContent;
        this.contents = builder.contents;
        this.name = builder.name;
    }

    public Role role() {
        return role;
    }

    /**
     * Returns the content set as a plain string, or {@code null} when it was set as a list of content
     * blocks instead (see {@link #contents()}).
     */
    public String content() {
        return content;
    }

    /**
     * Returns the content set as a list of content blocks, or {@code null} when it was set as a plain
     * string instead (see {@link #content()}). The list form is required to attach a
     * {@code prompt_cache_breakpoint} to the message.
     */
    public List<Content> contents() {
        return contents;
    }

    @JsonProperty("content")
    private Object contentForSerialization() {
        return contents != null ? contents : content;
    }

    public String name() {
        return name;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof SystemMessage && equalTo((SystemMessage) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(SystemMessage another) {
        return Objects.equals(role, another.role)
                && Objects.equals(content, another.content)
                && Objects.equals(contents, another.contents)
                && Objects.equals(name, another.name);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(role);
        h += (h << 5) + Objects.hashCode(content);
        h += (h << 5) + Objects.hashCode(contents);
        h += (h << 5) + Objects.hashCode(name);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "SystemMessage{" + "role=" + role + ", content=" + contentForSerialization() + ", name=" + name + "}";
    }

    public static SystemMessage from(String content) {
        return SystemMessage.builder().content(content).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String stringContent;
        private List<Content> contents;
        private String name;

        public Builder content(String content) {
            this.stringContent = content;
            this.contents = null;
            return this;
        }

        public Builder content(List<Content> content) {
            this.contents = content;
            this.stringContent = null;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public SystemMessage build() {
            return new SystemMessage(this);
        }
    }
}
