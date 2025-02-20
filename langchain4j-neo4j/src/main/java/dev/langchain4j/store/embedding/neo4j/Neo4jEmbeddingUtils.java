package dev.langchain4j.store.embedding.neo4j;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

public class Neo4jEmbeddingUtils {

    /* not-configurable strings, just used under-the-hood in `UNWIND $rows ...` statement */
    public static final String EMBEDDINGS_ROW_KEY = "embeddingRow";

    /* default configs */
    public static final String DEFAULT_ID_PROP = "id";
    public static final String DEFAULT_DATABASE_NAME = "neo4j";
    public static final String DEFAULT_EMBEDDING_PROP = "embedding";
    public static final String PROPS = "props";
    public static final String DEFAULT_IDX_NAME = "vector";
    public static final String DEFAULT_LABEL = "Document";
    public static final String DEFAULT_TEXT_PROP = "text";
    public static final long DEFAULT_AWAIT_INDEX_TIMEOUT = 60L;

    public static EmbeddingMatch<TextSegment> toEmbeddingMatch(Neo4jEmbeddingStore store, Record neo4jRecord) {
        Map<String, String> metaData = new HashMap<>();
        neo4jRecord.get("metadata").asMap().forEach((key, value) -> {
            if (!store.getNotMetaKeys().contains(key)) {
                String stringValue = value == null ? null : value.toString();
                metaData.put(key.replace(store.getMetadataPrefix(), ""), stringValue);
            }
        });

        Metadata metadata = new Metadata(metaData);

        Value text = neo4jRecord.get(store.getTextProperty());
        TextSegment textSegment = text.isNull() ? null : TextSegment.from(text.asString(), metadata);

        List<Float> embeddingList =
                neo4jRecord.get(store.getEmbeddingProperty()).asList(Value::asFloat);

        Embedding embedding = Embedding.from(embeddingList);

        return new EmbeddingMatch<>(
                neo4jRecord.get("score").asDouble(),
                neo4jRecord.get(store.getIdProperty()).asString(),
                embedding,
                textSegment);
    }

    public static Map<String, Object> toRecord(
            Neo4jEmbeddingStore store,
            int idx,
            List<String> ids,
            List<Embedding> embeddings,
            List<TextSegment> embedded) {
        String id = ids.get(idx);
        Embedding embedding = embeddings.get(idx);

        Map<String, Object> row = new HashMap<>();
        row.put(store.getIdProperty(), id);

        Map<String, Object> properties = new HashMap<>();
        if (embedded != null) {
            TextSegment segment = embedded.get(idx);
            properties.put(store.getTextProperty(), segment.text());
            Map<String, String> metadata = segment.metadata().asMap();
            metadata.forEach((k, v) -> properties.put(store.getMetadataPrefix() + k, Values.value(v)));
        }

        row.put(EMBEDDINGS_ROW_KEY, Values.value(embedding.vector()));
        row.put(PROPS, properties);
        return row;
    }

    public static Stream<List<Map<String, Object>>> getRowsBatched(
            Neo4jEmbeddingStore store, List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        int batchSize = 10_000;
        AtomicInteger batchCounter = new AtomicInteger();
        int total = ids.size();
        int batchNumber = (int) Math.ceil((double) total / batchSize);
        return IntStream.range(0, batchNumber).mapToObj(part -> {
            List<Map<String, Object>> maps =
                    ids.subList(Math.min(part * batchSize, total), Math.min((part + 1) * batchSize, total)).stream()
                            .map(i -> toRecord(store, batchCounter.getAndIncrement(), ids, embeddings, embedded))
                            .toList();
            return maps;
        });
    }
}
