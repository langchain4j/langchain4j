package dev.langchain4j.skills;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.nio.file.Path;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultFileSystemSkill that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(basePath, that.basePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), basePath);
    }

    @Override
    public String toString() {
        return "DefaultFileSystemSkill {"
                + " name = " + name()
                + ", description = " + description()
                + ", content = " + content()
                + ", resources = " + resources()
                + ", toolProviders = " + toolProviders()
                + ", basePath = " + basePath
                + " }";
    }

    /**
     * Returns a new builder pre-populated with the values from this skill.
     * Useful for creating a modified copy, e.g. adding tools to a filesystem-loaded skill:
     * <pre>{@code
     * FileSystemSkill skillWithTools = skill.toBuilder().tools(new MyTools()).build();
     * }</pre>
     */
    public Builder toBuilder() {
        return builder().copyFrom(this).basePath(basePath());
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
