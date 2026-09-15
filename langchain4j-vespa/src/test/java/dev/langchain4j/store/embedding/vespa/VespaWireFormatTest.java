package dev.langchain4j.store.embedding.vespa;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Records are written by the codec and read back by Retrofit's own Jackson converter, so the two
 * have to agree on the field names. Writing goes through a snake_case codec; reading relies on the
 * {@code @JsonNaming} the record still carries, which is what Retrofit's mapper sees.
 */
class VespaWireFormatTest {

    private final Json.JsonCodec codec = ProviderJson.codec(
            ProviderJsonSpec.builder().propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE).build());

    private static Record record() {
        return new Record(
                "id-1", null, new Record.Fields("id::doc::1", "hello", new Record.Fields.Vector(List.of(0.1f, 0.2f))));
    }

    @Test
    void should_write_a_record_with_snake_case_fields() {
        String json = codec.toJson(record());

        assertThat(json)
                .contains("\"text_segment\":\"hello\"")
                .contains("\"documentid\":\"id::doc::1\"")
                .contains("\"values\":[0.1,0.2]");
        assertThat(json).doesNotContain("textSegment");
    }

    @Test
    void retrofits_mapper_reads_back_what_the_codec_writes() throws Exception {
        // JacksonConverterFactory.create() builds a plain ObjectMapper, which is what this stands in for.
        Record read = new ObjectMapper().readValue(codec.toJson(record()), Record.class);

        assertThat(read.id()).isEqualTo("id-1");
        assertThat(read.fields().textSegment()).isEqualTo("hello");
        assertThat(read.fields().documentid()).isEqualTo("id::doc::1");
        assertThat(read.fields().vector().values()).containsExactly(0.1f, 0.2f);
    }
}
