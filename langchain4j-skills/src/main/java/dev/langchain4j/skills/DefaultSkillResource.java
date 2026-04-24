package dev.langchain4j.skills;

import dev.langchain4j.Experimental;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

@Experimental
public class DefaultSkillResource implements SkillResource {

    private final String relativePath;
    private final String content;

    public DefaultSkillResource(Builder builder) {
        this.relativePath = ensureNotBlank(builder.relativePath, "relativePath");
        this.content = ensureNotBlank(builder.content, "content");
    }

    @Override
    public String relativePath() {
        return relativePath;
    }

    @Override
    public String content() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultSkillResource that)) return false;
        return Objects.equals(relativePath, that.relativePath)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, content);
    }

    @Override
    public String toString() {
        return "DefaultSkillResource {"
                + " relativePath = " + relativePath
                + ", content = " + content
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String relativePath;
        private String content;

        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public DefaultSkillResource build() {
            return new DefaultSkillResource(this);
        }
    }
}
