package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateAuthClient;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.data.replication.model.ConsistencyLevel;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class WeaviateEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private static final String WEAVIATE_CLASS = "Default";

    private final WeaviateClient client;

    @Builder
    @SneakyThrows
    public WeaviateEmbeddingStoreImpl(String apiKey, String scheme, String host) {
        client = WeaviateAuthClient.apiKey(new Config(scheme, host), apiKey);

        val meta = client.misc().metaGetter().run();
        if (meta.getError() == null) {
            System.out.printf("meta.hostname: %s\n", meta.getResult().getHostname());
            System.out.printf("meta.version: %s\n", meta.getResult().getVersion());
            System.out.printf("meta.modules: %s\n", meta.getResult().getModules());
        } else {
            System.out.printf("Error: %s\n", meta.getError().getMessages());
        }
    }

    @SneakyThrows
    private static String generateUUI(String input) {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02x", b));
        String sha256Hash = sb.toString();
        return UUID.nameUUIDFromBytes(sha256Hash.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @Override
    public String add(Embedding embedding) {
        return null;
    }

    @Override
    public void add(String id, Embedding embedding) {

    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return null;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return null;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of embeddings and embedded must have the same size");
        }

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
//            ids.add(add(embeddings.get(i), embedded.get(i)));

            val props = new HashMap<String, Object>();
            props.put("text", embedded.get(i).text());

            val id = generateUUI(embedded.get(i).text());
            ids.add(id);

            val object = WeaviateObject.builder()
                    .className(WEAVIATE_CLASS)
                    .id(id)
//                .id("36ddd591-2dee-4e7e-a3cc-eb86d30a4305")
                    .properties(props)
                    .vector(embeddings.get(i).vectorAsList().toArray(new Float[0]))
                    .build();

            client.batch().objectsBatcher()
                    .withObject(object)
                    .withConsistencyLevel(ConsistencyLevel.ALL)  // default QUORUM
                    .run();

//        if (result.hasErrors()) {
//            System.out.println(result.getError());
//        }
//        System.out.println(result.getResult());

        }

        val getResult = client.data().objectsGetter()
                .withClassName(WEAVIATE_CLASS)
                .withConsistencyLevel(ConsistencyLevel.ALL)  // default QUORUM
                .run();

        if (getResult.hasErrors()) {
            System.out.println(getResult.getError());
        }
        System.out.println(getResult.getResult().size());
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return null;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minSimilarity) {
        val result = client.graphQL().get()
                .withClassName(WEAVIATE_CLASS)
                .withFields(Field.builder().name("text").build())
                .withNearVector(NearVectorArgument.builder()
                        .vector(referenceEmbedding.vectorAsList().toArray(new Float[0]))
                        .certainty((float) minSimilarity)
                        .build())
                .withLimit(maxResults)
                .run();

        if (result.hasErrors()) {
            System.out.println(result.getError());
            return null;
        }
        System.out.println(((Map)(result.getResult().getData())).get("Get"));

        return null;
    }

}
