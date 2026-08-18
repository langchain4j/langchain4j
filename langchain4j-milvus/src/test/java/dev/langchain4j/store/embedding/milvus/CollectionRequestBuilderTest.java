package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.buildQueryRequest;
import static org.assertj.core.api.Assertions.assertThat;

import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.dml.QueryParam;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionRequestBuilderTest {

    private static final String COLLECTION_NAME = "test_collection";
    private static final FieldDefinition FIELD_DEFINITION = new FieldDefinition("id", "text", "metadata", "vector");

    @Test
    void should_quote_and_escape_row_ids_in_query_expression() {
        List<String> rowIds = List.of("normal-id", "id'with'apostrophe", "id\"with-quote", "id\\with-backslash");

        QueryParam request = buildQueryRequest(COLLECTION_NAME, FIELD_DEFINITION, rowIds, ConsistencyLevelEnum.STRONG);

        assertThat(request.getExpr())
                .isEqualTo(
                        "id in [\"normal-id\", \"id'with'apostrophe\", \"id\\\"with-quote\", \"id\\\\with-backslash\"]");
    }

    @Test
    void should_build_query_expression_for_a_single_row_id() {
        QueryParam request =
                buildQueryRequest(COLLECTION_NAME, FIELD_DEFINITION, List.of("id-1"), ConsistencyLevelEnum.STRONG);

        assertThat(request.getExpr()).isEqualTo("id in [\"id-1\"]");
    }
}
