package dev.langchain4j.skills;

import dev.langchain4j.Experimental;

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
