package dev.langchain4j.skills;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class DefaultSkillFile implements SkillFile {

    private final String path;
    private final String body;

    public DefaultSkillFile(Builder builder) {
        this.path = ensureNotBlank(builder.path, "path");
        this.body = ensureNotBlank(builder.body, "body");
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String body() {
        return body;
    }

    // TODO eht
    // TODO toString: part of body, part of files

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String path;
        private String body;

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public DefaultSkillFile build() {
            return new DefaultSkillFile(this);
        }
    }
}
