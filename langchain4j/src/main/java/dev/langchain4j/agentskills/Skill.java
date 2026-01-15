package dev.langchain4j.agentskills;

import dev.langchain4j.Experimental;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents an Agent Skill that can be used to enhance AI capabilities.
 * <p>
 * A skill consists of frontmatter fields and instructions that guide
 * the AI in performing specific tasks. Skills follow the Agent Skills specification
 * from <a href="https://agentskills.io">agentskills.io</a>.
 * <p>
 * Frontmatter fields (from SKILL.md YAML header):
 * <ul>
 *   <li>{@code name} - Required. Max 64 characters, lowercase letters, numbers, and hyphens only.</li>
 *   <li>{@code description} - Required. Max 1024 characters. Describes what the skill does.</li>
 *   <li>{@code license} - Optional. License name or reference to a bundled license file.</li>
 *   <li>{@code compatibility} - Optional. Max 500 characters. Environment requirements.</li>
 *   <li>{@code metadata} - Optional. Arbitrary key-value mapping for additional metadata.</li>
 *   <li>{@code allowed-tools} - Optional. Space-delimited list of pre-approved tools.</li>
 * </ul>
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class Skill {

    private final String name;
    private final String description;
    private final String license;
    private final String compatibility;
    private final Map<String, Object> metadata;
    private final List<String> allowedTools;
    private final String instructions;
    private final Path path;

    private Skill(Builder builder) {
        this.name = ensureNotBlank(builder.name, "name");
        this.description = ensureNotBlank(builder.description, "description");
        this.license = builder.license;
        this.compatibility = builder.compatibility;
        this.metadata = copy(builder.metadata);
        this.allowedTools = copy(builder.allowedTools);
        this.instructions = builder.instructions;
        this.path = ensureNotNull(builder.path, "path");
    }

    /**
     * Returns the unique name of the skill.
     * <p>
     * The name must be 1-64 characters, lowercase letters, numbers, and hyphens only.
     *
     * @return the skill name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the description of the skill.
     * <p>
     * The description explains what the skill does and when it should be used.
     * Max 1024 characters.
     *
     * @return the skill description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the license under which the skill is distributed.
     *
     * @return the license, or null if not specified
     */
    public String license() {
        return license;
    }

    /**
     * Returns the compatibility requirements for the skill.
     * <p>
     * This may include environment requirements like intended product,
     * system packages, network access, etc. Max 500 characters.
     *
     * @return the compatibility string, or null if not specified
     */
    public String compatibility() {
        return compatibility;
    }

    /**
     * Returns arbitrary key-value metadata for the skill.
     *
     * @return the metadata map, or null if not specified
     */
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Returns the list of pre-approved tools the skill may use.
     * <p>
     * This is an experimental feature from the Agent Skills specification.
     *
     * @return the list of allowed tools, or null if not specified
     */
    public List<String> allowedTools() {
        return allowedTools;
    }

    /**
     * Returns the full instructions from SKILL.md markdown content.
     * <p>
     * This is the markdown body that follows the YAML frontmatter.
     *
     * @return the skill instructions
     */
    public String instructions() {
        return instructions;
    }

    /**
     * Returns the path to the skill directory.
     *
     * @return the skill directory path
     */
    public Path path() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Skill skill = (Skill) o;
        return Objects.equals(name, skill.name)
                && Objects.equals(description, skill.description)
                && Objects.equals(path, skill.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, path);
    }

    @Override
    public String toString() {
        return "Skill {"
                + " name = " + quoted(name)
                + ", description = " + quoted(description)
                + ", path = " + path
                + " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String license;
        private String compatibility;
        private Map<String, Object> metadata;
        private List<String> allowedTools;
        private String instructions;
        private Path path;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder license(String license) {
            this.license = license;
            return this;
        }

        public Builder compatibility(String compatibility) {
            this.compatibility = compatibility;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder allowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        public Skill build() {
            return new Skill(this);
        }
    }
}
