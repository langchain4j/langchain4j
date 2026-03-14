package dev.langchain4j.skills.validator.model;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Properties parsed from a skill's SKILL.md frontmatter.
 *
 * <p>Attributes:
 * <ul>
 *   <li>name: Skill name in kebab-case (required)
 *   <li>description: What the skill does and when the model should use it (required)
 *   <li>license: License for the skill (optional)
 *   <li>compatibility: Compatibility information for the skill (optional)
 *   <li>allowedTools: Tool patterns the skill requires (optional, experimental)
 *   <li>metadata: Key-value pairs for client-specific properties (defaults to empty map)
 * </ul>
 */
public class SkillProperties {
    private final String name;
    private final String description;
    private final String license;
    private final String compatibility;
    private final String allowedTools;
    private final Map<String, String> metadata;

    public SkillProperties(
            String name,
            String description,
            String license,
            String compatibility,
            String allowedTools,
            Map<String, String> metadata) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.license = license;
        this.compatibility = compatibility;
        this.allowedTools = allowedTools;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getLicense() {
        return license;
    }

    public String getCompatibility() {
        return compatibility;
    }

    public String getAllowedTools() {
        return allowedTools;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Convert to map, excluding null values and empty metadata.
     *
     * @return Map representation of the skill properties
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("description", description);

        if (license != null) {
            result.put("license", license);
        }
        if (compatibility != null) {
            result.put("compatibility", compatibility);
        }
        if (allowedTools != null) {
            result.put("allowed-tools", allowedTools);
        }
        if (!metadata.isEmpty()) {
            result.put("metadata", new HashMap<>(metadata));
        }

        return result;
    }

    /**
     * Convert to JSON object suitable for JSON serialization.
     *
     * @return JsonObject representation
     */
    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", description);

        if (license != null) {
            json.addProperty("license", license);
        }
        if (compatibility != null) {
            json.addProperty("compatibility", compatibility);
        }
        if (allowedTools != null) {
            json.addProperty("allowed-tools", allowedTools);
        }
        if (!metadata.isEmpty()) {
            JsonObject metadataObj = new JsonObject();
            metadata.forEach(metadataObj::addProperty);
            json.add("metadata", metadataObj);
        }

        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkillProperties that = (SkillProperties) o;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(license, that.license)
                && Objects.equals(compatibility, that.compatibility)
                && Objects.equals(allowedTools, that.allowedTools)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, license, compatibility, allowedTools, metadata);
    }

    @Override
    public String toString() {
        return "SkillProperties{"
                + "name='"
                + name
                + '\''
                + ", description='"
                + description
                + '\''
                + ", license='"
                + license
                + '\''
                + ", compatibility='"
                + compatibility
                + '\''
                + ", allowedTools='"
                + allowedTools
                + '\''
                + ", metadata="
                + metadata
                + '}';
    }

    /**
     * Builder for SkillProperties.
     */
    public static class Builder {
        private String name;
        private String description;
        private String license;
        private String compatibility;
        private String allowedTools;
        private Map<String, String> metadata = new HashMap<>();

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

        public Builder allowedTools(String allowedTools) {
            this.allowedTools = allowedTools;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        public Builder putMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public SkillProperties build() {
            return new SkillProperties(name, description, license, compatibility, allowedTools, metadata);
        }
    }
}
