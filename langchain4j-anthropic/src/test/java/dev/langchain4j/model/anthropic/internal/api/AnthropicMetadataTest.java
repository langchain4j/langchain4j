package dev.langchain4j.model.anthropic.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.anthropic.internal.client.Json;
import org.junit.jupiter.api.Test;

class AnthropicMetadataTest {


    @Test
    void should_create_metadata_with_userId() {
        // given
        String userId = "test-user-123";

        // when
        AnthropicMetadata metadata = new AnthropicMetadata(userId);

        // then
        assertThat(metadata.getUserId()).isEqualTo(userId);
    }

    @Test
    void should_create_metadata_using_builder() {
        // given
        String userId = "test-user-456";

        // when
        AnthropicMetadata metadata = AnthropicMetadata.builder().userId(userId).build();

        // then
        assertThat(metadata.getUserId()).isEqualTo(userId);
    }

    @Test
    void should_serialize_to_json_with_snake_case() throws Exception {
        // given
        AnthropicMetadata metadata =
                AnthropicMetadata.builder().userId("test-user-789").build();

        // when
        String json = Json.toJson(metadata);

        // then
        assertThat(json).isEqualToIgnoringWhitespace("{\"user_id\" : \"test-user-789\"}");
    }

    @Test
    void should_deserialize_from_json_with_snake_case() throws Exception {
        // given
        String json = "{\"user_id\":\"test-user-abc\"}";

        // when
        AnthropicMetadata metadata = Json.fromJson(json, AnthropicMetadata.class);

        // then
        assertThat(metadata.getUserId()).isEqualTo("test-user-abc");
    }

    @Test
    void should_not_serialize_null_userId() throws Exception {
        // given
        AnthropicMetadata metadata = new AnthropicMetadata();

        // when
        String json = Json.toJson(metadata);

        // then
        assertThat(json).isEqualToIgnoringWhitespace("{}");
    }

    @Test
    void should_handle_equals_and_hashCode() {
        // given
        AnthropicMetadata metadata1 =
                AnthropicMetadata.builder().userId("user1").build();
        AnthropicMetadata metadata2 =
                AnthropicMetadata.builder().userId("user1").build();
        AnthropicMetadata metadata3 =
                AnthropicMetadata.builder().userId("user2").build();

        // then
        assertThat(metadata1).isEqualTo(metadata2);
        assertThat(metadata1).isNotEqualTo(metadata3);
        assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
        assertThat(metadata1.hashCode()).isNotEqualTo(metadata3.hashCode());
    }

    @Test
    void should_have_meaningful_toString() {
        // given
        AnthropicMetadata metadata =
                AnthropicMetadata.builder().userId("test-user").build();

        // when
        String toString = metadata.toString();

        // then
        assertThat(toString).contains("AnthropicMetadata");
        assertThat(toString).contains("userId='test-user'");
    }

    @Test
    void should_create_builder_from_existing_metadata() {
        // given
        AnthropicMetadata original =
                AnthropicMetadata.builder().userId("original-user").build();

        // when
        AnthropicMetadata copy = original.toBuilder().build();

        // then
        assertThat(copy.getUserId()).isEqualTo("original-user");
        assertThat(copy).isEqualTo(original);
    }
}
