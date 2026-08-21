package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

class WireJsonTest {

    static class Dto {

        @JsonProperty
        public String name;

        @JsonProperty
        public Integer maxOutputTokens;
    }

    @Test
    void tolerates_properties_the_provider_added_later() {
        // A provider adding a field must never break deserialization, in any module.
        Json.JsonCodec codec = WireJson.codec(WireJsonSpec.builder().build());

        Dto dto = codec.fromJson("{\"name\":\"a\",\"brandNewField\":123}", Dto.class);

        assertThat(dto.name).isEqualTo("a");
    }

    @Test
    void tolerates_unknown_properties_under_snake_case_too() {
        Json.JsonCodec codec = WireJson.codec(WireJsonSpec.builder()
                .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
                .build());

        Dto dto = codec.fromJson("{\"name\":\"a\",\"brand_new_field\":123}", Dto.class);

        assertThat(dto.name).isEqualTo("a");
    }

    @Test
    void snake_case_naming_is_applied_on_serialization() {
        Json.JsonCodec codec = WireJson.codec(WireJsonSpec.builder()
                .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
                .build());

        Dto dto = new Dto();
        dto.maxOutputTokens = 5;

        assertThat(codec.toJson(dto)).contains("max_output_tokens");
    }

    @Test
    void inclusion_controls_whether_nulls_are_written() {
        Dto dto = new Dto();

        assertThat(WireJson.codec(WireJsonSpec.builder().build()).toJson(dto))
                .contains("\"name\":null");

        assertThat(WireJson.codec(WireJsonSpec.builder()
                                .inclusion(WireJsonSpec.Inclusion.NON_NULL)
                                .build())
                        .toJson(dto))
                .isEqualToIgnoringWhitespace("{}");
    }
}
