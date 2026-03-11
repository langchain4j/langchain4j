package dev.langchain4j.skills.validator;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillValidatorTest {
    private final SkillValidator validator = new SkillValidator();

    @Test
    void shouldValidateCorrectSkill() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "test-skill");
        metadata.put("description", "A test skill");

        List<String> errors = validator.validateMetadata(metadata, null);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldReturnErrorIfPathDoesNotExist() {
        List<String> errors = validator.validate(Path.of("/nonexistent/path"));

        assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("does not exist"));
    }

    @Test
    void shouldReturnErrorIfPathIsNotDirectory(@TempDir Path tempDir) throws Exception {
        Path file = Files.createFile(tempDir.resolve("file.txt"));

        List<String> errors = validator.validate(file);

        assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("Not a directory"));
    }

    @Test
    void shouldReturnErrorIfSkillMdMissing(@TempDir Path tempDir) {
        List<String> errors = validator.validate(tempDir);

        assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("SKILL.md"));
    }

    @Test
    void shouldReturnErrorIfNameMissing(@TempDir Path tempDir) throws Exception {
        String content = "---\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anyMatch(e -> e.contains("Missing required field"));
        assertThat(errors).anyMatch(e -> e.contains("name"));
    }

    @Test
    void shouldReturnErrorIfDescriptionMissing(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anyMatch(e -> e.contains("Missing required field"));
        assertThat(errors).anyMatch(e -> e.contains("description"));
    }

    @Test
    void shouldReturnErrorIfNameIsNotLowercase(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: TestSkill\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("lowercase"));
    }

    @Test
    void shouldReturnErrorIfNameStartsWithHyphen(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: -test-skill\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("cannot start or end with a hyphen"));
    }

    @Test
    void shouldReturnErrorIfNameEndsWithHyphen(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill-\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("cannot start or end with a hyphen"));
    }

    @Test
    void shouldReturnErrorIfNameHasConsecutiveHyphens(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test--skill\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("consecutive hyphens"));
    }

    @Test
    void shouldReturnErrorIfNameTooLong(@TempDir Path tempDir) throws Exception {
        String longName = "a".repeat(100);
        String content = "---\nname: " + longName + "\ndescription: A test skill\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("exceeds"));
    }

    @Test
    void shouldReturnErrorIfDescriptionTooLong(@TempDir Path tempDir) throws Exception {
        String longDesc = "a".repeat(2000);
        String content = "---\nname: test-skill\ndescription: " + longDesc + "\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("exceeds"));
    }

    @Test
    void shouldReturnErrorIfDirectoryNameDoesntMatchSkillName(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("wrong-name");
        Files.createDirectory(skillDir);
        String content = "---\nname: test-skill\ndescription: A test skill\n---\nBody";
        Files.writeString(skillDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(skillDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Directory name"));
    }

    @Test
    void shouldReturnErrorForUnexpectedFields(@TempDir Path tempDir) throws Exception {
        String content = "---\nname: test-skill\ndescription: A test skill\nunknown-field: value\n---\nBody";
        Files.writeString(tempDir.resolve("SKILL.md"), content);

        List<String> errors = validator.validate(tempDir);

        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Unexpected fields"));
    }

    @Test
    void shouldValidateMetadataWithValidFields(@TempDir Path tempDir) throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "test-skill");
        metadata.put("description", "A test skill");
        metadata.put("license", "Apache 2.0");
        metadata.put("compatibility", "Java 17+");

        List<String> errors = validator.validateMetadata(metadata, null);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldAllowHyphensInSkillName() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "my-test-skill");
        metadata.put("description", "A test skill");

        List<String> errors = validator.validateMetadata(metadata, null);

        assertThat(errors).isEmpty();
    }

    @Test
    void shouldAllowNumbersInSkillName() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "skill-v2");
        metadata.put("description", "A test skill");

        List<String> errors = validator.validateMetadata(metadata, null);

        assertThat(errors).isEmpty();
    }
}
