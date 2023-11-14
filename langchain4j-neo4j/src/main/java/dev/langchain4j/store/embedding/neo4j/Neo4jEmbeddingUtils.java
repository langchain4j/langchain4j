package dev.langchain4j.store.embedding.neo4j;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Neo4jEmbeddingUtils {
    
    /* not-configurable strings, used in `UNWIND $rows ...` statement */
    public static final String EMBEDDINGS_ROW_KEY = "embeddingRow";
    public static final String ID_ROW_KEY = "id";
    
    /* default configs */
    public static final String DEFAULT_EMBEDDING_PROP = "embedding";
    public static final String PROPS = "props";
    public static final String DEFAULT_IDX_NAME = "langchain-embedding-index";
    public static final String DEFAULT_LABEL = "Document";
    public static final String DEFAULT_TEXT_PROP = "text";
    

    public static EmbeddingMatch<TextSegment> toEmbeddingMatch(Neo4jEmbeddingStore store, Record neo4jRecord) {
        var node = neo4jRecord.get("node").asNode();

        var metaData = new HashMap<String, String>();
        node.keys().forEach(key -> {
            Set<String> notMetaKeys = Set.of(ID_ROW_KEY, store.getEmbeddingProperty(), store.getText());
            if (!notMetaKeys.contains(key)) {
                metaData.put(key.replace(store.getMetadataPrefix(), ""), node.get(key).asString());
            }
        });

        Metadata metadata = new Metadata(metaData);

        Value text = node.get(store.getText());
        TextSegment textSegment = text.isNull()
                ? null
                : TextSegment.from(text.asString(), metadata);
        List<Number> embeddingList = node.get(store.getEmbeddingProperty())
                .asList(Value::asNumber);

        Embedding embedding = new Embedding(toFloatArray(embeddingList));

        return new EmbeddingMatch<>(neo4jRecord.get("score").asDouble(), node.get(ID_ROW_KEY).asString(), embedding, textSegment);
    }
    
    public static float[] toFloatArray(List<Number> numberList) {
        float[] embeddingFloat = new float[numberList.size()];
        int i = 0;
        for(Number num: numberList) {
            embeddingFloat[i++] = num.floatValue();
        }
        return embeddingFloat;
    }

    public static Map<String, Object> toRecord(Neo4jEmbeddingStore store, int idx, List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        String id = ids.get(idx);
        Embedding embedding = embeddings.get(idx);

        Map<String, Object> row = new HashMap<>();
        row.put(ID_ROW_KEY, id);

        Map<String, Object> properties = new HashMap<>();
        if (embedded != null) {
            TextSegment segment = embedded.get(idx);
            properties.put(store.getText(), segment.text());
            Map<String, String> metadata = segment.metadata().asMap();
            metadata.forEach((k, v) -> properties.put(store.getMetadataPrefix() + k, Values.value(v)));
        }

        row.put(EMBEDDINGS_ROW_KEY, Values.value(embedding.vector()));
        row.put(PROPS, properties);
        return row;
    }
}
