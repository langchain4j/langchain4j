package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.transport.McpJson;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * The tree-to-plain-values conversion feeds listener callbacks and the deprecated JsonNode APIs,
 * so it has to preserve values exactly and agree with the mapper-based path.
 */
class McpJsonConversionsTest {

    private static final String JSON =
            """
            {"big":123456789012345678901234567890,"long":9007199254740993,"int":5,"d":1.5,"s":"x","b":true,"n":null}""";

    @Test
    void an_integer_too_wide_for_a_long_should_not_be_truncated() {
        assertThat(McpJsonConversions.toMap(McpJson.parse(JSON)))
                .containsEntry("big", new BigInteger("123456789012345678901234567890"));
    }

    @Test
    void integral_values_should_keep_their_narrowest_exact_type() {
        var map = McpJsonConversions.toMap(McpJson.parse(JSON));
        assertThat(map).containsEntry("int", 5);
        assertThat(map).containsEntry("long", 9007199254740993L);
    }

    @Test
    void it_should_agree_with_the_mapper_based_conversion() {
        assertThat(McpJsonConversions.toMap(McpJson.parse(JSON))).isEqualTo(McpJson.toMap(JSON));
    }

    @Test
    void scalars_and_nulls_should_round_trip() {
        var map = McpJsonConversions.toMap(McpJson.parse(JSON));
        assertThat(map).containsEntry("d", 1.5).containsEntry("s", "x").containsEntry("b", true);
        assertThat(map).containsKey("n");
        assertThat(map.get("n")).isNull();
    }
}
