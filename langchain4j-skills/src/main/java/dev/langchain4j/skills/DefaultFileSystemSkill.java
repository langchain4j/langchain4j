package dev.langchain4j.skills;

import dev.langchain4j.Experimental;

import java.nio.file.Path;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Experimental
public class DefaultFileSystemSkill extends AbstractSkill implements FileSystemSkill {

    private final Path basePath;

    public DefaultFileSystemSkill(Builder builder) {
        super(builder);
        this.basePath = ensureNotNull(builder.basePath, "basePath");
    }

    @Override
    public Path basePath() {
        return basePath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractSkill.BaseBuilder<Builder> {

        private Path basePath;

        public Builder basePath(Path basePath) {
            this.basePath = basePath;
            return this;
        }

        public DefaultFileSystemSkill build() {
            return new DefaultFileSystemSkill(this);
        }
    }
}
