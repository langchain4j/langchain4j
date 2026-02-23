package dev.langchain4j.skills;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class DefaultSkill implements Skill {

    private final String name;
    private final String description;
    private final String body;
    private final List<SkillFile> files;
    private final Path directory;

    public DefaultSkill(Builder builder) {
        this.name = ensureNotBlank(builder.name, "name");
        this.description = ensureNotBlank(builder.description, "description");
        this.body = ensureNotBlank(builder.body, "body");
        this.files = copy(builder.files);
        this.directory = builder.directory;
        validateUniqueFilePaths(this.files);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String body() {
        return body;
    }

    @Override
    public List<SkillFile> files() {
        return files;
    }

    public Path directory() { // TODO
        return directory;
    }

    // TODO eht
    // TODO toString: part of body, part of files

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String body;
        private List<? extends SkillFile> files;
        private Path directory;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder files(List<? extends SkillFile> files) {
            this.files = files;
            return this;
        }

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public DefaultSkill build() {
            return new DefaultSkill(this);
        }
    }

    private static void validateUniqueFilePaths(List<SkillFile> files) {
        Set<String> seenPaths = new HashSet<>();

        for (SkillFile file : files) {
            String path = file.path();

            if (!seenPaths.add(path)) {
                throw new IllegalStateException("Duplicate file.path detected: '%s'".formatted(path)); // TODO name
            }
        }
    }
}
