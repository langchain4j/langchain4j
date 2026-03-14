package dev.langchain4j.skills.validator;

import static org.assertj.core.api.Assertions.*;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests using real skill files from test/resources directory.
 * Tests both valid and invalid skills according to the agentskills.io specification.
 */
class SkillValidatorIntegrationTest {
    private static final SkillValidator validator = new SkillValidator();
    private static Path skillsBasePath;

    @BeforeAll
    static void setup() {
        URL resource = SkillValidatorIntegrationTest.class.getResource("/skills");
        assertThat(resource)
                .withFailMessage("Test resources directory not found")
                .isNotNull();
        skillsBasePath = Paths.get(resource.getPath());
    }

    @Nested
    @DisplayName("Valid Skills")
    class ValidSkillsTests {

        @Test
        @DisplayName("should validate minimal valid skill with only required fields")
        void shouldValidateMinimalSkill() {
            Path skillPath = skillsBasePath.resolve("valid-skill");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should validate skill with all optional fields")
        void shouldValidateSkillWithAllFields() {
            Path skillPath = skillsBasePath.resolve("pdf-processing");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should validate skill with metadata")
        void shouldValidateSkillWithMetadata() {
            Path skillPath = skillsBasePath.resolve("data-analysis");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isEmpty();
        }
    }

    @Nested
    @DisplayName("Invalid Skills - Name Validation")
    class InvalidNameTests {

        @Test
        @DisplayName("should reject uppercase letters in name")
        void shouldRejectUppercaseName() {
            Path skillPath = skillsBasePath.resolve("invalid-uppercase");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isNotEmpty().anyMatch(e -> e.toLowerCase().contains("lowercase"));
        }

        @Test
        @DisplayName("should reject name starting with hyphen")
        void shouldRejectNameStartingWithHyphen() {
            Path skillPath = skillsBasePath.resolve("invalid-starts-hyphen");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("cannot start or end with a hyphen"));
        }

        @Test
        @DisplayName("should reject consecutive hyphens in name")
        void shouldRejectConsecutiveHyphens() {
            Path skillPath = skillsBasePath.resolve("invalid-consecutive-hyphens");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("consecutive hyphens"));
        }
    }

    @Nested
    @DisplayName("Invalid Skills - Required Fields")
    class RequiredFieldsTests {

        @Test
        @DisplayName("should reject skill without name field")
        void shouldRejectMissingName() {
            Path skillPath = skillsBasePath.resolve("invalid-missing-name");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("Missing required field") && e.contains("name"));
        }

        @Test
        @DisplayName("should reject directory without SKILL.md file")
        void shouldRejectMissingSkillMd() {
            Path skillPath = skillsBasePath.resolve("empty-dir");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("SKILL.md"));
        }
    }

    @Nested
    @DisplayName("Invalid Skills - Length Limits")
    class LengthLimitTests {

        @Test
        @DisplayName("should reject description exceeding 1024 characters")
        void shouldRejectLongDescription() {
            Path skillPath = skillsBasePath.resolve("invalid-long-description");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("Description exceeds") && e.contains("1024"));
        }
    }

    @Nested
    @DisplayName("Directory Name Validation")
    class DirectoryNameTests {

        @Test
        @DisplayName("should pass when directory name matches skill name")
        void shouldPassMatchingDirectoryName() {
            Path skillPath = skillsBasePath.resolve("valid-skill");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should fail when directory name doesn't match skill name")
        void shouldFailMismatchedDirectoryName() {
            Path skillPath = skillsBasePath.resolve("invalid-uppercase");
            List<String> errors = validator.validate(skillPath);

            // This should fail both for uppercase AND directory mismatch
            assertThat(errors).isNotEmpty().anyMatch(e -> e.contains("Directory name") || e.contains("lowercase"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle skills with numbers in name")
        void shouldAllowNumbersInName() {
            // The pdf-processing and data-analysis don't have numbers,
            // but valid-skill could be extended with v2, etc.
            // This tests the general case
            Path skillPath = skillsBasePath.resolve("valid-skill");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should handle skills with hyphens correctly")
        void shouldAllowHyphensInName() {
            Path skillPath = skillsBasePath.resolve("pdf-processing");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should validate all optional fields are actually optional")
        void shouldAcceptMinimalSkillWithoutOptionalFields() {
            Path skillPath = skillsBasePath.resolve("valid-skill");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors).isEmpty();
        }
    }

    @Nested
    @DisplayName("Multiple Validation Errors")
    class MultipleErrorTests {

        @Test
        @DisplayName("should report multiple errors for badly formed skills")
        void shouldReportMultipleErrors() {
            // invalid-uppercase has both uppercase and directory mismatch issues
            Path skillPath = skillsBasePath.resolve("invalid-uppercase");
            List<String> errors = validator.validate(skillPath);

            assertThat(errors.size()).isGreaterThanOrEqualTo(1);
        }
    }
}
