package dev.langchain4j.store.embedding.vearch;

import com.google.gson.Gson;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class VearchEmbeddingStoreT {

    private static VearchEmbeddingStore vearchEmbeddingStore;

    @BeforeAll
    public static void before() {
        String routerBaseUrl = "http://47.93.124.244:9001";
        String dbName = "ts_db";
        String spaceName = "ts_space";
        String vectorFieldName = "ts_vector";
        String textSegmentFieldName = "ts_keyword";
        vearchEmbeddingStore = new VearchEmbeddingStore(routerBaseUrl, dbName, spaceName, vectorFieldName, textSegmentFieldName, Duration.ofMinutes(1));
    }
    @Test
    public void add() {
        float[] vectorAry = new float[]{0.1f,0.2f,0.3f,0.4f,0.5f,0.6f,0.7f,0.8f,0.9f,0.91f,0.92f,0.93f,0.94f,0.95f,0.96f,0.97f,0.98f,0.99f,0.991f,0.992f,0.993f,0.994f,0.995f,0.996f,0.997f,0.998f,0.999f,0.9991f,0.9992f,0.9993f,0.9994f,0.9995f};
        String id = vearchEmbeddingStore.add(new Embedding(vectorAry));
        System.out.println("id = " + id);
    }

    @Test
    public void add_2() {
        float[] vectorAry = new float[]{0.1f,0.2f,0.3f,0.4f,0.5f,0.6f,0.7f,0.8f,0.9f,0.91f,0.92f,0.93f,0.94f,0.95f,0.96f,0.97f,0.98f,0.99f,0.991f,0.992f,0.993f,0.994f,0.995f,0.996f,0.997f,0.998f,0.999f,0.9991f,0.9992f,0.9993f,0.9994f,0.9995f};
        Embedding embedding = new Embedding(vectorAry);
        TextSegment textSegment = TextSegment.from("ts_textSegment");
        String id = vearchEmbeddingStore.add(embedding, textSegment);
        System.out.println("id = " + id);
    }

    @Test
    public void findRelevant() {
        List<Float> feature = new ArrayList<>();
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);
        feature.add(0.3f);
        feature.add(0.4f);
        feature.add(0.5f);
        feature.add(0.1f);
        feature.add(0.2f);

        Embedding referenceEmbedding = Embedding.from(feature);
        int maxResults = 3;
        double minScore = 0.9;
        List<EmbeddingMatch<TextSegment>> relevant = vearchEmbeddingStore.findRelevant(referenceEmbedding, maxResults, minScore);
        System.out.println(new Gson().toJson(relevant));
    }
}
