package dev.langchain4j.skills.validator.parser;

import dev.langchain4j.skills.validator.error.ParseError;
import dev.langchain4j.skills.validator.error.ValidationError;
import dev.langchain4j.skills.validator.model.SkillProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * YAML frontmatter parsing for SKILL.md files.
 */
public class FrontmatterParser {
    private final Yaml yaml = new Yaml();

    /**
     * Find the SKILL.md file in a skill directory.
     *
     * <p>Prefers SKILL.md (uppercase) but accepts skill.md (lowercase).
     *
     * @param skillDir Path to the skill directory
     * @return Path to the SKILL.md file, or null if not found
     */
    public Path findSkillMd(Path skillDir) {
        Path upperCase = skillDir.resolve("SKILL.md");
        if (Files.exists(upperCase)) {
            return upperCase;
        }

        Path lowerCase = skillDir.resolve("skill.md");
        if (Files.exists(lowerCase)) {
            return lowerCase;
        }

        return null;
    }

    /**
     * Parse YAML frontmatter from SKILL.md content.
     *
     * @param content Raw content of SKILL.md file
     * @return Tuple of (metadata map, markdown body)
     * @throws ParseError If frontmatter is missing or invalid
     */
    public FrontmatterResult parseFrontmatter(String content) throws ParseError {
        if (!content.startsWith("---")) {
            throw new ParseError("SKILL.md must start with YAML frontmatter (---)");
        }

        String[] parts = content.split("---", 3);
        if (parts.length < 3) {
            throw new ParseError("SKILL.md frontmatter not properly closed with ---");
        }

        String frontmatterStr = parts[1];
        String body = parts[2].strip();

        Map<String, Object> metadata;
        try {
            Object parsed = yaml.load(frontmatterStr);
            if (!(parsed instanceof Map)) {
                throw new ParseError("SKILL.md frontmatter must be a YAML mapping");
            }
            metadata = (Map<String, Object>) parsed;
        } catch (YAMLException e) {
            throw new ParseError("Invalid YAML in frontmatter: " + e.getMessage(), e);
        }

        // Normalize metadata map - ensure string keys and values
        Map<String, Object> normalizedMetadata = new HashMap<>();
        for (Map.Entry<?, ?> entry : metadata.entrySet()) {
            String key = entry.getKey().toString();
            if ("metadata".equals(key) && entry.getValue() instanceof Map) {
                Map<String, String> nestedMetadata = new HashMap<>();
                Map<?, ?> nestedMap = (Map<?, ?>) entry.getValue();
                nestedMap.forEach((k, v) -> nestedMetadata.put(String.valueOf(k), String.valueOf(v)));
                normalizedMetadata.put(key, nestedMetadata);
            } else {
                normalizedMetadata.put(key, entry.getValue());
            }
        }

        return new FrontmatterResult(normalizedMetadata, body);
    }

    /**
     * Read skill properties from SKILL.md frontmatter.
     *
     * <p>This function parses the frontmatter and returns properties. It does NOT perform full
     * validation. Use {@link dev.langchain4j.skills.validator.SkillValidator#validate(Path)} for that.
     *
     * @param skillDir Path to the skill directory
     * @return SkillProperties with parsed metadata
     * @throws ParseError If SKILL.md is missing or has invalid YAML
     * @throws ValidationError If required fields (name, description) are missing
     */
    public SkillProperties readProperties(Path skillDir) throws ParseError, ValidationError {
        skillDir = Objects.requireNonNull(skillDir, "skillDir cannot be null").toAbsolutePath();

        Path skillMd = findSkillMd(skillDir);
        if (skillMd == null) {
            throw new ParseError("SKILL.md not found in " + skillDir);
        }

        String content;
        try {
            content = Files.readString(skillMd);
        } catch (Exception e) {
            throw new ParseError("Failed to read SKILL.md: " + e.getMessage(), e);
        }

        FrontmatterResult result = parseFrontmatter(content);
        Map<String, Object> metadata = result.getMetadata();

        // Validate required fields
        if (!metadata.containsKey("name")) {
            throw new ValidationError("Missing required field in frontmatter: name");
        }
        if (!metadata.containsKey("description")) {
            throw new ValidationError("Missing required field in frontmatter: description");
        }

        Object nameObj = metadata.get("name");
        Object descObj = metadata.get("description");

        if (!(nameObj instanceof String) || ((String) nameObj).isBlank()) {
            throw new ValidationError("Field 'name' must be a non-empty string");
        }
        if (!(descObj instanceof String) || ((String) descObj).isBlank()) {
            throw new ValidationError("Field 'description' must be a non-empty string");
        }

        String name = ((String) nameObj).strip();
        String description = ((String) descObj).strip();

        String license = getStringValue(metadata, "license");
        String compatibility = getStringValue(metadata, "compatibility");
        String allowedTools = getStringValue(metadata, "allowed-tools");
        Map<String, String> metadataMap = null;

        if (metadata.containsKey("metadata") && metadata.get("metadata") instanceof Map) {
            metadataMap = (Map<String, String>) metadata.get("metadata");
        }

        return SkillProperties.builder()
                .name(name)
                .description(description)
                .license(license)
                .compatibility(compatibility)
                .allowedTools(allowedTools)
                .metadata(metadataMap)
                .build();
    }

    /**
     * Helper to get string value from metadata map, handling null and type safety.
     */
    private String getStringValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    /**
     * Result wrapper for parsed frontmatter.
     */
    public static class FrontmatterResult {
        private final Map<String, Object> metadata;
        private final String body;

        public FrontmatterResult(Map<String, Object> metadata, String body) {
            this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
            this.body = Objects.requireNonNull(body, "body cannot be null");
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public String getBody() {
            return body;
        }
    }
}
