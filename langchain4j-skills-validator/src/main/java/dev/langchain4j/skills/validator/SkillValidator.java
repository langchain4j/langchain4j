package dev.langchain4j.skills.validator;

import dev.langchain4j.skills.validator.error.ParseError;
import dev.langchain4j.skills.validator.parser.FrontmatterParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Skill validation logic.
 */
public class SkillValidator {
    private static final int MAX_SKILL_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final int MAX_COMPATIBILITY_LENGTH = 500;

    private static final Set<String> ALLOWED_FIELDS =
            Set.of("name", "description", "license", "allowed-tools", "metadata", "compatibility");

    private final FrontmatterParser parser = new FrontmatterParser();

    /**
     * Validate a skill directory.
     *
     * @param skillDir Path to the skill directory
     * @return List of validation error messages. Empty list means valid.
     */
    public List<String> validate(Path skillDir) {
        skillDir = Objects.requireNonNull(skillDir, "skillDir cannot be null").toAbsolutePath();

        if (!Files.exists(skillDir)) {
            return List.of("Path does not exist: " + skillDir);
        }

        if (!Files.isDirectory(skillDir)) {
            return List.of("Not a directory: " + skillDir);
        }

        Path skillMd = parser.findSkillMd(skillDir);
        if (skillMd == null) {
            return List.of("Missing required file: SKILL.md");
        }

        try {
            String content = Files.readString(skillMd);
            FrontmatterParser.FrontmatterResult result = parser.parseFrontmatter(content);
            return validateMetadata(result.getMetadata(), skillDir);
        } catch (ParseError e) {
            return List.of(e.getMessage());
        } catch (Exception e) {
            return List.of("Error reading or parsing SKILL.md: " + e.getMessage());
        }
    }

    /**
     * Validate parsed skill metadata.
     *
     * @param metadata Parsed YAML frontmatter dictionary
     * @param skillDir Optional path to skill directory (for name-directory match check)
     * @return List of validation error messages. Empty list means valid.
     */
    public List<String> validateMetadata(Map<String, Object> metadata, Path skillDir) {
        List<String> errors = new ArrayList<>();

        // Check for unknown fields
        errors.addAll(validateMetadataFields(metadata));

        // Validate name
        if (!metadata.containsKey("name")) {
            errors.add("Missing required field in frontmatter: name");
        } else {
            errors.addAll(validateName(metadata.get("name"), skillDir));
        }

        // Validate description
        if (!metadata.containsKey("description")) {
            errors.add("Missing required field in frontmatter: description");
        } else {
            errors.addAll(validateDescription(metadata.get("description")));
        }

        // Validate compatibility if present
        if (metadata.containsKey("compatibility")) {
            errors.addAll(validateCompatibility(metadata.get("compatibility")));
        }
        
        if (metadata.containsKey("allowed-tools")) {
            errors.addAll(validateAllowedTools(metadata.get("allowed-tools")));
        }

        return errors;
    }
    
    /**
     * Validate the {@code allowed-tools} field from SKILL.md frontmatter.
     *
     * <p>The {@code allowed-tools} field specifies which external tools a skill
     * requires to function correctly. The value must be a whitespace-separated
     * list of tool identifiers.
     *
     * <p>Each entry may optionally include a constraint in parentheses.
     *
     * <p>Examples of valid values:
     * <pre>
     * allowed-tools: Bash(git:*) Bash(jq:*) Read
     * allowed-tools: Bash(python3:*) Read
     * allowed-tools: Read
     * </pre>
     *
     *
     * <p>If any token does not match the expected format, a validation error
     * is returned for that entry.
     *
     * @param allowedToolsObj the raw {@code allowed-tools} value parsed from the YAML frontmatter
     * @return a list of validation error messages. An empty list indicates the value is valid.
     */
    private List<String> validateAllowedTools(Object allowedToolsObj) {
        List<String> errors = new ArrayList<>();

        if (!(allowedToolsObj instanceof String)) {
            errors.add("Field 'allowed-tools' must be a string");
            return errors;
        }

        String allowedTools = ((String) allowedToolsObj).trim();

        if (allowedTools.isEmpty()) {
            errors.add("Field 'allowed-tools' cannot be empty");
        }

        return errors;
    }


    /**
     * Validate that only allowed fields are present.
     */
    private List<String> validateMetadataFields(Map<String, Object> metadata) {
        List<String> errors = new ArrayList<>();

        Set<String> extraFields = new HashSet<>(metadata.keySet());
        extraFields.removeAll(ALLOWED_FIELDS);

        if (!extraFields.isEmpty()) {
            List<String> sortedExtra = new ArrayList<>(extraFields);
            sortedExtra.sort(String::compareTo);
            errors.add("Unexpected fields in frontmatter: "
                    + sortedExtra
                    + ". Only "
                    + new ArrayList<>(ALLOWED_FIELDS)
                    + " are allowed.");
        }

        return errors;
    }

    /**
     * Validate skill name format and directory match.
     *
     * <p>Skill names support i18n characters (Unicode letters) plus hyphens. Names must be
     * lowercase and cannot start/end with hyphens.
     */
    private List<String> validateName(Object nameObj, Path skillDir) {
        List<String> errors = new ArrayList<>();

        if (!(nameObj instanceof String)) {
            errors.add("Field 'name' must be a non-empty string");
            return errors;
        }

        String name = (String) nameObj;
        if (name.isBlank()) {
            errors.add("Field 'name' must be a non-empty string");
            return errors;
        }

        // Normalize to NFKC form
        name = Normalizer.normalize(name.strip(), Normalizer.Form.NFKC);

        // Check length
        if (name.length() > MAX_SKILL_NAME_LENGTH) {
            errors.add("Skill name '"
                    + name
                    + "' exceeds "
                    + MAX_SKILL_NAME_LENGTH
                    + " character limit ("
                    + name.length()
                    + " chars)");
        }

        // Check lowercase
        if (!name.equals(name.toLowerCase())) {
            errors.add("Skill name '" + name + "' must be lowercase");
        }

        // Check start/end hyphens
        if (name.startsWith("-") || name.endsWith("-")) {
            errors.add("Skill name cannot start or end with a hyphen");
        }

        // Check consecutive hyphens
        if (name.contains("--")) {
            errors.add("Skill name cannot contain consecutive hyphens");
        }

        // Check valid characters
        if (!isValidSkillName(name)) {
            errors.add("Skill name '"
                    + name
                    + "' contains invalid characters. "
                    + "Only letters, digits, and hyphens are allowed.");
        }

        // Check directory match
        if (skillDir != null) {
            String dirName = Normalizer.normalize(skillDir.getFileName().toString(), Normalizer.Form.NFKC);
            if (!dirName.equals(name)) {
                errors.add("Directory name '" + skillDir.getFileName() + "' must match skill name '" + name + "'");
            }
        }

        return errors;
    }

    /**
     * Check if name contains only valid characters.
     */
    private boolean isValidSkillName(String name) {
        Pattern pattern = Pattern.compile("^[\\p{L}\\p{N}-]+$");
        return pattern.matcher(name).matches();
    }

    /**
     * Validate description format.
     */
    private List<String> validateDescription(Object descObj) {
        List<String> errors = new ArrayList<>();

        if (!(descObj instanceof String)) {
            errors.add("Field 'description' must be a non-empty string");
            return errors;
        }

        String description = (String) descObj;
        if (description.isBlank()) {
            errors.add("Field 'description' must be a non-empty string");
            return errors;
        }

        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("Description exceeds "
                    + MAX_DESCRIPTION_LENGTH
                    + " character limit ("
                    + description.length()
                    + " chars)");
        }

        return errors;
    }

    /**
     * Validate compatibility format.
     */
    private List<String> validateCompatibility(Object compatObj) {
        List<String> errors = new ArrayList<>();

        if (!(compatObj instanceof String)) {
            errors.add("Field 'compatibility' must be a string");
            return errors;
        }

        String compatibility = (String) compatObj;
        if (compatibility.length() > MAX_COMPATIBILITY_LENGTH) {
            errors.add("Compatibility exceeds "
                    + MAX_COMPATIBILITY_LENGTH
                    + " character limit ("
                    + compatibility.length()
                    + " chars)");
        }

        return errors;
    }
}
