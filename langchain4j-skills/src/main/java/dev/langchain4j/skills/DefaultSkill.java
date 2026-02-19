package dev.langchain4j.skills;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class DefaultSkill implements Skill {

    private final String name;
    private final String description;
    private final String body;
    private final List<SkillReference> references;

    public DefaultSkill(Builder builder) {
        this.name = ensureNotBlank(builder.name, "name");
        this.description = ensureNotBlank(builder.description, "description");
        this.body = ensureNotBlank(builder.body, "body");
        this.references = copy(builder.references);
        validateUniqueReferencePaths(this.references);
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
    public List<SkillReference> references() {
        return references;
    }

    // TODO eht
    // TODO toString: part of body, part of references

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String body;
        private List<? extends SkillReference> references;

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

        public Builder references(List<? extends SkillReference> references) {
            this.references = references;
            return this;
        }

        public DefaultSkill build() {
            return new DefaultSkill(this);
        }
    }

    private static void validateUniqueReferencePaths(List<SkillReference> references) {
        Set<String> seenPaths = new HashSet<>();

        for (SkillReference reference : references) {
            String path = reference.path();

            if (!seenPaths.add(path)) {
                throw new IllegalStateException("Duplicate reference.path detected: '%s'".formatted(path)); // TODO name
            }
        }
    }
}
