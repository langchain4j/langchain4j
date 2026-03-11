package dev.langchain4j.skills.validator.model;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillPropertiesTest {

    @Test
    void shouldCreateSkillPropertiesWithRequiredFields() {
        SkillProperties props = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .build();

        assertThat(props.getName()).isEqualTo("my-skill");
        assertThat(props.getDescription()).isEqualTo("A test skill");
        assertThat(props.getLicense()).isNull();
        assertThat(props.getMetadata()).isEmpty();
    }

    @Test
    void shouldCreateSkillPropertiesWithAllFields() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");

        SkillProperties props = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .license("Apache 2.0")
                .compatibility("Java 17+")
                .allowedTools("tool1, tool2")
                .metadata(metadata)
                .build();

        assertThat(props.getName()).isEqualTo("my-skill");
        assertThat(props.getDescription()).isEqualTo("A test skill");
        assertThat(props.getLicense()).isEqualTo("Apache 2.0");
        assertThat(props.getCompatibility()).isEqualTo("Java 17+");
        assertThat(props.getAllowedTools()).isEqualTo("tool1, tool2");
        assertThat(props.getMetadata()).containsEntry("key1", "value1");
    }

    @Test
    void shouldConvertToMapExcludingNullValues() {
        SkillProperties props = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .license("Apache 2.0")
                .build();

        Map<String, Object> map = props.toMap();

        assertThat(map).containsKeys("name", "description", "license");
        assertThat(map).doesNotContainKeys("compatibility", "allowed-tools");
    }

    @Test
    void shouldConvertToMapExcludingEmptyMetadata() {
        SkillProperties props = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .build();

        Map<String, Object> map = props.toMap();

        assertThat(map).doesNotContainKey("metadata");
    }

    @Test
    void shouldConvertToMapIncludingNonEmptyMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key1", "value1");

        SkillProperties props = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .metadata(metadata)
                .build();

        Map<String, Object> map = props.toMap();

        assertThat(map).containsKey("metadata");
        assertThat((Map<String, String>) map.get("metadata")).containsEntry("key1", "value1");
    }

    @Test
    void shouldThrowNullPointerExceptionForNullName() {
        assertThatThrownBy(() -> SkillProperties.builder()
                        .name(null)
                        .description("A test skill")
                        .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionForNullDescription() {
        assertThatThrownBy(() -> SkillProperties.builder()
                        .name("my-skill")
                        .description(null)
                        .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCreateCopyOfMetadataMap() {
        Map<String, String> originalMetadata = new HashMap<>();
        originalMetadata.put("key1", "value1");

        SkillProperties props = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .metadata(originalMetadata)
                .build();

        originalMetadata.put("key2", "value2");

        // Original modification should not affect the properties instance
        assertThat(props.getMetadata()).doesNotContainKey("key2");
    }

    @Test
    void shouldReturnUnmodifiableMetadata() {
        SkillProperties props = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .putMetadata("key1", "value1")
                .build();

        Map<String, String> metadata = props.getMetadata();

        assertThatThrownBy(() -> metadata.put("key2", "value2")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldImplementEquality() {
        SkillProperties props1 = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .license("Apache 2.0")
                .build();

        SkillProperties props2 = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .license("Apache 2.0")
                .build();

        assertThat(props1).isEqualTo(props2);
        assertThat(props1.hashCode()).isEqualTo(props2.hashCode());
    }

    @Test
    void shouldImplementEqualityWithDifferentValues() {
        SkillProperties props1 = SkillProperties.builder()
                .name("my-skill")
                .description("A test skill")
                .build();

        SkillProperties props2 = SkillProperties.builder()
                .name("my-skill")
                .description("A different skill")
                .build();

        assertThat(props1).isNotEqualTo(props2);
    }
}
