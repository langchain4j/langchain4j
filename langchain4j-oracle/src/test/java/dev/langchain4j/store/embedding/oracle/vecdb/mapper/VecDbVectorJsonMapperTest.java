package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies vector upsert, deletion, and listing JSON at the LangChain4j/VecDB boundary. */
class VecDbVectorJsonMapperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Verifies serialization of an ID and dense vector without embedded content. */
    @Test
    void testMapsVectorOnlyRecord() throws JsonProcessingException {
        String json = VecDbVectorJsonMapper.toJson(
                List.of("vector-1"), List.of(new Embedding(new float[] {0.1f, -0.2f, 0.3f})));

        assertJsonEquals(json, """
                [
                  {
                    "id": "vector-1",
                    "dense_vector": [0.1, -0.2, 0.3]
                  }
                ]
                """);
    }

    /** Verifies serialization of text, user metadata, and the dense vector for a segment. */
    @Test
    void testMapsTextSegmentRecord() throws JsonProcessingException {
        Metadata metadata = new Metadata().put("tenant", "acme").put("page", 3);
        TextSegment segment = TextSegment.from("Oracle VecDB", metadata);

        String json = VecDbVectorJsonMapper.toJson(
                List.of("vector-1"), List.of(new Embedding(new float[] {0.1f, 0.2f})), List.of(segment));

        assertJsonEquals(json, """
                [
                  {
                    "id": "vector-1",
                    "dense_vector": [0.1, 0.2],
                    "metadata": {
                      "tenant": "acme",
                      "page": 3,
                      "text": "Oracle VecDB"
                    }
                  }
                ]
                """);
    }

    /** Verifies that batch upsert JSON preserves caller input order. */
    @Test
    void testPreservesBatchOrder() throws JsonProcessingException {
        String json = VecDbVectorJsonMapper.toJson(
                List.of("first", "second"),
                List.of(new Embedding(new float[] {1.0f, 0.0f}), new Embedding(new float[] {0.0f, 1.0f})));

        JsonNode vectors = readJson(json);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0).get("id").asText()).isEqualTo("first");
        assertThat(vectors.get(1).get("id").asText()).isEqualTo("second");
    }

    /** Verifies serialization of embedding IDs for {@code DELETE_VECTORS}. */
    @Test
    void testMapsIdsForDeletion() throws JsonProcessingException {
        assertJsonEquals(VecDbVectorJsonMapper.idsToJson(List.of("first", "second")), "[\"first\",\"second\"]");
    }

    /** Verifies LIST_VECTORS parsing and removal of the internal compatibility text property. */
    @Test
    void testParsesListedVectorsAndRemovesCompatibilityText() {
        String response = """
                {
                  "items": [
                    {
                      "id": "first",
                      "metadata": {
                        "text": "First segment",
                        "tenant": "acme",
                        "page": 3
                      }
                    },
                    {
                      "id": "second",
                      "metadata": null
                    }
                  ]
                }
                """;

        List<VecDbVectorJsonMapper.ListedVector> vectors = VecDbVectorJsonMapper.vectorsFromListResponse(response);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0).id()).isEqualTo("first");
        assertThat(vectors.get(0).metadata().toMap())
                .containsEntry("tenant", "acme")
                .containsEntry("page", 3)
                .doesNotContainKey("text");
        assertThat(vectors.get(1).id()).isEqualTo("second");
        assertThat(vectors.get(1).metadata().toMap()).isEmpty();
    }

    /** Verifies that IDs extracted from LIST_VECTORS retain database response order. */
    @Test
    void testExtractsIdsFromListResponseInDatabaseOrder() {
        String response = """
                {
                  "items": [
                    {"id": "second"},
                    {"id": "first"}
                  ]
                }
                """;

        assertThat(VecDbVectorJsonMapper.idsFromListResponse(response)).containsExactly("second", "first");
    }

    /** Verifies rejection of batch IDs and embeddings with different sizes. */
    @Test
    void testRejectsMismatchedIdsAndEmbeddings() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.toJson(
                        List.of("first", "second"), List.of(new Embedding(new float[] {1.0f}))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids.size() 2 is not equal to embeddings.size() 1");
    }

    /** Verifies rejection of batch IDs and text segments with different sizes. */
    @Test
    void testRejectsMismatchedIdsAndTextSegments() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.toJson(
                        List.of("first"),
                        List.of(new Embedding(new float[] {1.0f})),
                        List.of(TextSegment.from("first"), TextSegment.from("second"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids.size() 1 is not equal to segments.size() 2");
    }

    /** Verifies rejection of null entries before constructing an upsert payload. */
    @Test
    void testRejectsNullBatchEntry() {
        assertThatThrownBy(
                        () -> VecDbVectorJsonMapper.toJson(List.of("first"), java.util.Collections.singletonList(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null entry at index 0 in embeddings");
    }

    /** Verifies rejection of blank caller-provided embedding IDs. */
    @Test
    void testRejectsBlankId() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.toJson(List.of(" "), List.of(new Embedding(new float[] {1.0f}))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id cannot be null or blank");
    }

    /** Verifies that user metadata cannot overwrite the reserved compatibility text property. */
    @Test
    void testRejectsReservedTextMetadataDuringIngestion() {
        TextSegment segment =
                TextSegment.from("Store-managed text", new Metadata().put("text", "Caller-provided text"));

        assertThatThrownBy(() -> VecDbVectorJsonMapper.toJson(
                        List.of("vector-1"), List.of(new Embedding(new float[] {1.0f})), List.of(segment)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved key \"text\"");
    }

    /** Verifies that deletion requires at least one embedding ID. */
    @Test
    void testRejectsEmptyIdCollectionForDeletion() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.idsToJson(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ids cannot be null or empty");
    }

    /** Verifies that malformed LIST_VECTORS JSON is rejected. */
    @Test
    void testRejectsMalformedListResponse() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.vectorsFromListResponse("not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("response is not valid JSON");
    }

    /** Verifies that LIST_VECTORS must contain an items array. */
    @Test
    void testRejectsListResponseWithoutItemsArray() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.vectorsFromListResponse("{\"items\":{}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("\"items\" must be an array");
    }

    /** Verifies that every listed vector must contain a string ID. */
    @Test
    void testRejectsListResponseWithoutStringId() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.vectorsFromListResponse("{\"items\":[{\"id\":42}]}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must contain a string \"id\"");
    }

    /** Verifies that listed metadata must be a JSON object when present. */
    @Test
    void testRejectsListResponseWithNonObjectMetadata() {
        assertThatThrownBy(() -> VecDbVectorJsonMapper.vectorsFromListResponse(
                        "{\"items\":[{\"id\":\"vector-1\",\"metadata\":[]}]}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("object or null \"metadata\"");
    }

    private static void assertJsonEquals(String actual, String expected) throws JsonProcessingException {
        assertThat(readJson(actual)).isEqualTo(readJson(expected));
    }

    private static JsonNode readJson(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readTree(json);
    }
}
