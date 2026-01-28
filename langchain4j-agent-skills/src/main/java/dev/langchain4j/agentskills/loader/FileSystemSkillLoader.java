package dev.langchain4j.agentskills.loader;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentskills.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads skills from the file system by scanning for SKILL.md files.
 * <p>
 * This loader follows the Agent Skills specification from
 * <a href="https://agentskills.io/specification">agentskills.io</a>.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class FileSystemSkillLoader implements SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSkillLoader.class);
    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n(.*)$", Pattern.DOTALL);
    private static final Pattern SKILL_NAME_PATTERN =
            Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    @Override
    public List<Skill> loadSkillsFromDirectory(Path directory) {
        List<Skill> skills = new ArrayList<>();

        if (!Files.isDirectory(directory)) {
            log.warn("Skill directory does not exist: {}", directory);
            return skills;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.getFileName().toString().equals(SKILL_FILE_NAME))
                    .forEach(skillMdPath -> {
                        try {
                            Skill skill = loadSkill(skillMdPath.getParent());
                            if (skill != null) {
                                skills.add(skill);
                            }
                        } catch (Exception e) {
                            log.error("Failed to load skill from: {}", skillMdPath, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan directory for skills: {}", directory, e);
        }

        return skills;
    }

    @Override
    public Skill loadSkill(Path skillDirectory) {
        Path skillMdPath = skillDirectory.resolve(SKILL_FILE_NAME);

        if (!Files.exists(skillMdPath)) {
            log.warn("SKILL.md not found in: {}", skillDirectory);
            return null;
        }

        try {
            String content = Files.readString(skillMdPath);
            return parseSkillMd(content, skillDirectory);
        } catch (IOException e) {
            log.error("Failed to read SKILL.md: {}", skillMdPath, e);
            return null;
        }
    }

    private Skill parseSkillMd(String content, Path skillDirectory) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);

        if (!matcher.matches()) {
            log.warn("Invalid SKILL.md format (missing frontmatter) in: {}", skillDirectory);
            return null;
        }

        String frontmatter = matcher.group(1);
        String instructions = matcher.group(2).trim();

        Map<String, Object> metadata = new HashMap<>();
        Map<String, String> fields = parseYamlFrontmatter(frontmatter, metadata);

        String name = fields.get("name");
        String description = fields.get("description");

        if (name == null || name.isBlank()) {
            log.warn("Missing 'name' in SKILL.md frontmatter: {}", skillDirectory);
            return null;
        }

        if (description == null || description.isBlank()) {
            log.warn("Missing 'description' in SKILL.md frontmatter: {}", skillDirectory);
            return null;
        }

        // Validate name format
        if (!isValidSkillName(name)) {
            throw new IllegalArgumentException("Invalid skill name '" + name + "'. "
                    + "Must be 1-64 chars, lowercase letters, numbers, and hyphens only, "
                    + "cannot start or end with hyphen");
        }

        // Validate description length
        if (description.length() > 1024) {
            throw new IllegalArgumentException("Description exceeds 1024 characters in skill '" + name + "'");
        }

        // Validate compatibility length
        String compatibility = fields.get("compatibility");
        if (compatibility != null && compatibility.length() > 500) {
            throw new IllegalArgumentException("Compatibility exceeds 500 characters in skill '" + name + "'");
        }

        return Skill.builder()
                .name(name)
                .description(description)
                .license(fields.get("license"))
                .compatibility(compatibility)
                .metadata(metadata.isEmpty() ? null : metadata)
                .allowedTools(parseAllowedTools(fields.get("allowed-tools")))
                .instructions(instructions)
                .path(skillDirectory)
                .build();
    }

    /**
     * Parses YAML frontmatter.
     * <p>
     * Note: This is a simplified parser. For production use with complex YAML,
     * consider using SnakeYAML or similar library.
     *
     * @param frontmatter the YAML content between --- markers
     * @param metadata    output map for nested metadata block (will be populated if metadata exists)
     * @return map of top-level fields
     */
    private Map<String, String> parseYamlFrontmatter(String frontmatter, Map<String, Object> metadata) {
        Map<String, String> result = new HashMap<>();
        String[] lines = frontmatter.split("\\r?\\n");

        boolean inMetadataBlock = false;

        for (String line : lines) {
            // Skip empty lines and comments
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            // Check if this is an indented line (part of metadata block)
            boolean isIndented = line.startsWith("  ") || line.startsWith("\t");

            if (isIndented && inMetadataBlock) {
                // Parse nested metadata key-value
                String trimmedLine = line.trim();
                int colonIndex = trimmedLine.indexOf(':');
                if (colonIndex > 0) {
                    String key = trimmedLine.substring(0, colonIndex).trim();
                    String value = trimmedLine.substring(colonIndex + 1).trim();
                    metadata.put(key, removeQuotes(value));
                }
                continue;
            }

            // Top-level field
            inMetadataBlock = false;
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                // Check if this starts the metadata block
                if ("metadata".equals(key) && value.isEmpty()) {
                    inMetadataBlock = true;
                    continue;
                }

                result.put(key, removeQuotes(value));
            }
        }

        return result;
    }

    private String removeQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean isValidSkillName(String name) {
        if (name == null || name.isEmpty() || name.length() > 64) {
            return false;
        }
        return SKILL_NAME_PATTERN.matcher(name).matches();
    }

    private List<String> parseAllowedTools(String allowedTools) {
        if (allowedTools == null || allowedTools.isBlank()) {
            return null;
        }
        return Arrays.asList(allowedTools.split("\\s+"));
    }
}
