package dev.langchain4j.store.embedding.chroma;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CollectionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static Stream<Arguments> should_report_the_distance_function_of_the_collection() {
        return Stream.of(
                Arguments.of("{\"configuration_json\":{\"hnsw\":{\"space\":\"cosine\"}}}", "cosine"),
                Arguments.of("{\"configuration_json\":{\"hnsw\":{\"space\":\"l2\"}}}", "l2"),
                Arguments.of("{\"configuration_json\":{\"hnsw_configuration\":{\"space\":\"cosine\"}}}", "cosine"),
                Arguments.of("{\"configuration_json\":{\"spann\":{\"space\":\"ip\"}}}", "ip"),
                Arguments.of("{\"metadata\":{\"hnsw:space\":\"cosine\"}}", "cosine"),
                Arguments.of("{\"metadata\":{\"hnsw:space\":\"ip\"}}", "ip"),
                Arguments.of("{\"configuration_json\":{\"hnsw\":null,\"spann\":null}}", "l2"),
                Arguments.of("{\"configuration_json\":{}}", "l2"),
                Arguments.of("{\"metadata\":{}}", "l2"),
                Arguments.of("{}", "l2"));
    }

    @ParameterizedTest
    @MethodSource
    void should_report_the_distance_function_of_the_collection(String json, String expected) throws Exception {
        Collection collection = OBJECT_MAPPER.readValue(json, Collection.class);

        assertThat(collection.distanceFunction()).isEqualTo(expected);
    }

    @Test
    void should_prefer_the_metadata_distance_function_over_the_configured_one() throws Exception {
        String json = """
                {
                  "configuration_json": {"hnsw_configuration": {"space": "l2"}},
                  "metadata": {"hnsw:space": "cosine"}
                }
                """;

        Collection collection = OBJECT_MAPPER.readValue(json, Collection.class);

        assertThat(collection.distanceFunction()).isEqualTo("cosine");
    }

    @Test
    void should_read_the_distance_function_from_a_full_collection_response_of_chroma_0_5_x() throws Exception {
        // chroma 0.5.x reports the default "l2" in "configuration_json" even though the collection uses cosine
        String json = "{\"id\":\"8f335b05-d821-4a7b-a002-40355abbe1b8\",\"name\":\"probe_cosine\","
                + "\"configuration_json\":{\"hnsw_configuration\":{\"space\":\"l2\",\"ef_construction\":100,\"M\":16,"
                + "\"_type\":\"HNSWConfigurationInternal\"},\"_type\":\"CollectionConfigurationInternal\"},"
                + "\"metadata\":{\"hnsw:space\":\"cosine\"},\"dimension\":null,\"tenant\":\"default_tenant\","
                + "\"database\":\"default_database\",\"version\":0}";

        Collection collection = OBJECT_MAPPER.readValue(json, Collection.class);

        assertThat(collection.distanceFunction()).isEqualTo("cosine");
    }

    @Test
    void should_read_the_distance_function_from_a_full_collection_response_of_chroma_1_x() throws Exception {
        String json = "{\"id\":\"cad5dce9-b301-43de-bca4-97dbbd8cff97\",\"name\":\"probe_l2\","
                + "\"configuration_json\":{\"hnsw\":{\"space\":\"l2\",\"ef_construction\":100,\"max_neighbors\":16},"
                + "\"spann\":null,\"embedding_function\":null},"
                + "\"metadata\":null,\"dimension\":null,\"tenant\":\"default_tenant\",\"version\":0}";

        Collection collection = OBJECT_MAPPER.readValue(json, Collection.class);

        assertThat(collection.getId()).isEqualTo("cad5dce9-b301-43de-bca4-97dbbd8cff97");
        assertThat(collection.getName()).isEqualTo("probe_l2");
        assertThat(collection.distanceFunction()).isEqualTo("l2");
    }
}
