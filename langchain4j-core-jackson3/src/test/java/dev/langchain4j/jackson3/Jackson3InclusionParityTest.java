package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Jackson 2's {@code serializationInclusion(NON_NULL)} sets value <em>and</em> content inclusion, so
 * a null map value is left out. Providers rely on that: Bedrock rejects an explicit null where it
 * accepts an absent field.
 */
class Jackson3InclusionParityTest {

    private static final Json.JsonCodec CODEC = ProviderJson.codec(
            ProviderJsonSpec.builder().inclusion(ProviderJsonSpec.Inclusion.NON_NULL).build());

    @Test
    void a_null_map_value_is_not_written() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("input_type", "search_document");
        parameters.put("truncate", null);

        assertThat(CODEC.toJson(parameters)).isEqualTo("{\"input_type\":\"search_document\"}");
    }

    @Test
    void a_null_field_is_not_written() {
        assertThat(CODEC.toJson(new Pojo("x", null))).isEqualTo("{\"set\":\"x\"}");
    }

    static class Pojo {
        public String set;
        public String unset;

        Pojo(String set, String unset) {
            this.set = set;
            this.unset = unset;
        }
    }
}
