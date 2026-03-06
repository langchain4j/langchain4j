package dev.langchain4j.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

class ToolSpecificationJsonTest {

    @Test
    void toJson_should_reject_null() {
        assertThatThrownBy(() -> ToolSpecification.fromJson(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_round_trip_with_parameters_and_metadata() {
        ToolSpecification original = ToolSpecification.builder()
                .name("get_weather")
                .description("Gets the weather for a location")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("location", "city name")
                        .addEnumProperty("unit", java.util.List.of("CELSIUS", "FAHRENHEIT"))
                        .required("location")
                        .build())
                .addMetadata("cache", true)
                .build();

        String json = original.toJson();
        ToolSpecification restored = ToolSpecification.fromJson(json);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void should_round_trip_without_parameters() {
        ToolSpecification original = ToolSpecification.builder()
                .name("get_time")
                .description("Gets the current time")
                .build();

        String json = original.toJson();
        ToolSpecification restored = ToolSpecification.fromJson(json);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void should_round_trip_name_only() {
        ToolSpecification original = ToolSpecification.builder().name("ping").build();

        String json = original.toJson();
        ToolSpecification restored = ToolSpecification.fromJson(json);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void should_reject_non_object_parameters() {
        String json = "{\"name\":\"test\",\"parameters\":{\"type\":\"string\"}}";
        assertThatThrownBy(() -> ToolSpecification.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameters");
    }

    @Test
    void should_reject_non_object_metadata() {
        String json = "{\"name\":\"test\",\"metadata\":\"bad\"}";
        assertThatThrownBy(() -> ToolSpecification.fromJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");
    }
}
