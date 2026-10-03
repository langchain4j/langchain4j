package dev.langchain4j.rag.content.retriever.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.model.SearchType;

/**
 * Integration tests for {@link BedrockKnowledgeBaseContentRetriever}.
 * <br>
 * These tests require:
 * <ul>
 *   <li>AWS credentials configured (AWS_SECRET_ACCESS_KEY environment variable)</li>
 *   <li>A Knowledge Base ID set in the BEDROCK_KNOWLEDGE_BASE_ID environment variable</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BEDROCK_KNOWLEDGE_BASE_ID", matches = ".+")
class BedrockKnowledgeBaseContentRetrieverIT {

    private static final String KNOWLEDGE_BASE_ID = System.getenv("BEDROCK_KNOWLEDGE_BASE_ID");

    @Test
    void should_retrieve_content_from_knowledge_base() {
        // given
        BedrockKnowledgeBaseContentRetriever retriever = BedrockKnowledgeBaseContentRetriever.builder()
                .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                .maxResults(5)
                .build();

        Query query = Query.from("What is this knowledge base about?");

        // when
        List<Content> contents = retriever.retrieve(query);

        // then
        assertThat(contents).isNotNull();
        assertThat(contents).isNotEmpty();
        for (Content content : contents) {
            assertThat(content.textSegment()).isNotNull();
            assertThat(content.textSegment().text()).isNotBlank();
        }
    }

    @Test
    void should_retrieve_content_with_custom_region() {
        // given
        BedrockKnowledgeBaseContentRetriever retriever = BedrockKnowledgeBaseContentRetriever.builder()
                .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                .region(Region.US_EAST_1)
                .maxResults(3)
                .build();

        Query query = Query.from("Tell me something");

        // when
        List<Content> contents = retriever.retrieve(query);

        // then
        assertThat(contents).isNotNull();
    }

    @Test
    void should_filter_by_min_score() {
        // given
        BedrockKnowledgeBaseContentRetriever retriever = BedrockKnowledgeBaseContentRetriever.builder()
                .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                .maxResults(10)
                .minScore(0.5)
                .build();

        Query query = Query.from("Some query text");

        // when
        List<Content> contents = retriever.retrieve(query);

        // then
        assertThat(contents).isNotNull();
        for (Content content : contents) {
            Object score = content.metadata().get(dev.langchain4j.rag.content.ContentMetadata.SCORE);
            if (score != null) {
                assertThat((Double) score).isGreaterThanOrEqualTo(0.5);
            }
        }
    }

    @Test
    void should_use_semantic_search_type() {
        // given
        BedrockKnowledgeBaseContentRetriever retriever = BedrockKnowledgeBaseContentRetriever.builder()
                .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                .searchType(SearchType.SEMANTIC)
                .maxResults(3)
                .build();

        Query query = Query.from("What information is available?");

        // when
        List<Content> contents = retriever.retrieve(query);

        // then
        assertThat(contents).isNotNull();
    }

    @Test
    void should_fail_with_invalid_knowledge_base_id() {
        // given
        BedrockKnowledgeBaseContentRetriever retriever = BedrockKnowledgeBaseContentRetriever.builder()
                .knowledgeBaseId("INVALID_KB_ID")
                .build();

        Query query = Query.from("Test query");

        // when/then
        assertThatThrownBy(() -> retriever.retrieve(query)).isInstanceOf(Exception.class);
    }

    @Test
    void should_fail_when_knowledge_base_id_is_blank() {
        // when/then
        assertThatThrownBy(() -> BedrockKnowledgeBaseContentRetriever.builder()
                        .knowledgeBaseId("")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_fail_when_knowledge_base_id_is_null() {
        // when/then
        assertThatThrownBy(() -> BedrockKnowledgeBaseContentRetriever.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_respect_timeout() {
        // given
        BedrockKnowledgeBaseContentRetriever retriever = BedrockKnowledgeBaseContentRetriever.builder()
                .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                .timeout(Duration.ofMillis(1))
                .build();

        Query query = Query.from("Test query");

        // when/then
        assertThatThrownBy(() -> retriever.retrieve(query)).isInstanceOf(Exception.class);
    }

    @Test
    void should_enable_request_logging() {
        // given
        BedrockKnowledgeBaseContentRetriever retriever = BedrockKnowledgeBaseContentRetriever.builder()
                .knowledgeBaseId(KNOWLEDGE_BASE_ID)
                .logRequests(true)
                .logResponses(true)
                .maxResults(1)
                .build();

        Query query = Query.from("Test query with logging");

        // when
        List<Content> contents = retriever.retrieve(query);

        // then
        assertThat(contents).isNotNull();
    }
}
