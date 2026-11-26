package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.filter.Filter;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElasticsearchStringMetadataFilterIT {

    private static final ElasticsearchClientHelper HELPER = new ElasticsearchClientHelper();
    private static final Embedding EMBEDDING = Embedding.from(new float[] {0.1f, 0.2f});
    private static final String TODAY = "2026-11-21";
    private static final String YESTERDAY = "2026-11-20";

    private String indexName;
    private ElasticsearchEmbeddingStore store;
    private String longSourceUrl;

    @BeforeAll
    static void startServices() throws IOException {
        HELPER.startServices();
        assertThat(HELPER.client).isNotNull();
    }

    @AfterAll
    static void stopServices() throws IOException {
        HELPER.stopServices();
    }

    @BeforeEach
    void createStore() throws IOException {
        indexName = randomUUID();
        HELPER.removeDataStore(indexName);
        longSourceUrl = "https://docs.example.com/guide/" + "segment/".repeat(40);
        assertThat(longSourceUrl).hasSizeGreaterThan(256);
        store = ElasticsearchEmbeddingStore.builder()
                .configuration(ElasticsearchConfigurationFullText.builder().build())
                .client(HELPER.client)
                .indexName(indexName)
                .build();
    }

    @AfterEach
    void removeDataStore() throws IOException {
        HELPER.removeDataStore(indexName);
    }

    @Test
    void should_match_date_like_strings_long_values_and_whole_string_ranges() throws IOException {
        add("stale chunk one", metadata(YESTERDAY, "Banana", "https://example.com/short", 2020));
        add("stale chunk two", metadata(YESTERDAY, "alpha zulu", longSourceUrl, 2020));
        add("fresh chunk", metadata(TODAY, "mango", "https://example.com/other", 2026));
        visible();

        assertThat(search(metadataKey("ingested_on").isEqualTo(TODAY))).containsExactly("fresh chunk");
        assertThat(search(metadataKey("ingested_on").isNotEqualTo(TODAY)))
                .containsExactlyInAnyOrder("stale chunk one", "stale chunk two");
        assertThat(search(metadataKey("ingested_on").isIn(YESTERDAY)))
                .containsExactlyInAnyOrder("stale chunk one", "stale chunk two");
        assertThat(search(metadataKey("ingested_on").isNotIn(TODAY)))
                .containsExactlyInAnyOrder("stale chunk one", "stale chunk two");

        assertThat(search(metadataKey("source").isEqualTo(longSourceUrl))).containsExactly("stale chunk two");
        assertThat(search(metadataKey("source").isNotEqualTo(longSourceUrl)))
                .containsExactlyInAnyOrder("stale chunk one", "fresh chunk");

        assertThat(search(metadataKey("title").isGreaterThan("m"))).containsExactly("fresh chunk");
        assertThat(search(metadataKey("title").isLessThan("b")))
                .containsExactlyInAnyOrder("stale chunk one", "stale chunk two");

        assertThat(search(metadataKey("year").isEqualTo(2026))).containsExactly("fresh chunk");
        assertThat(search(metadataKey("year").isGreaterThan(2020))).containsExactly("fresh chunk");
        assertThat(search(metadataKey("status").isEqualTo("published"))).containsExactly("fresh chunk");

        store.removeAll(metadataKey("source").isEqualTo(longSourceUrl));
        visible();
        assertThat(search(null)).containsExactlyInAnyOrder("stale chunk one", "fresh chunk");

        store.removeAll(metadataKey("ingested_on").isNotEqualTo(TODAY));
        visible();
        assertThat(search(null)).containsExactly("fresh chunk");
    }

    @Test
    void should_keep_string_filters_after_the_index_is_recreated() throws IOException {
        add("first chunk", metadata(YESTERDAY, "Banana", longSourceUrl, 2020));
        visible();
        store.removeAll();

        add("fresh chunk", metadata(TODAY, "mango", "https://example.com/other", 2026));
        visible();

        assertThat(search(metadataKey("ingested_on").isEqualTo(TODAY))).containsExactly("fresh chunk");
        assertThat(search(metadataKey("ingested_on").isNotEqualTo(TODAY))).isEmpty();
    }

    private void add(String text, Metadata metadata) {
        store.add(EMBEDDING, TextSegment.from(text, metadata));
    }

    private static Metadata metadata(String ingestedOn, String title, String source, int year) {
        return new Metadata()
                .put("ingested_on", ingestedOn)
                .put("title", title)
                .put("source", source)
                .put("year", year)
                .put("status", TODAY.equals(ingestedOn) ? "published" : "archived");
    }

    private List<String> search(Filter filter) {
        return store
                .fullTextSearchMatches(FullTextSearchRequest.builder()
                        .textQuery("chunk")
                        .maxResults(10)
                        .filter(filter)
                        .build())
                .stream()
                .map(EmbeddingMatch::embedded)
                .map(TextSegment::text)
                .toList();
    }

    private void visible() throws IOException {
        HELPER.refreshIndex(indexName);
    }
}
