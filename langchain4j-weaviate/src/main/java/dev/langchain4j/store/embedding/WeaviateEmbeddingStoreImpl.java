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
import java.util.stream.Collectors;

public class WeaviateEmbeddingStoreImpl implements EmbeddingStore<TextSegment> {

    private static final String DEFAULT_CLASS = "Default";
    private static final String METADATA_TEXT_SEGMENT = "text";
    private static final String ADDITIONALS = "_additional";

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

    private static EmbeddingMatch<TextSegment> toEmbeddingMatch(Map<String, ?> item) {
        val additional = (Map<String, ?>) item.get(ADDITIONALS);

        return new EmbeddingMatch<>(
                (String) additional.get("id"),
                Embedding.from(((List<Double>) additional.get("vector")).stream().map(Double::floatValue).collect(Collectors.toList())),
                TextSegment.from((String) item.get(METADATA_TEXT_SEGMENT)),
                (Double) additional.get("certainty"));
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
            props.put(METADATA_TEXT_SEGMENT, embedded.get(i).text());

            val id = generateUUI(embedded.get(i).text());
            ids.add(id);

            val object = WeaviateObject.builder()
                    .className(DEFAULT_CLASS)
                    .id(id)
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
                .withClassName(DEFAULT_CLASS)
                .withConsistencyLevel(ConsistencyLevel.ALL)
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
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minCertainty) {
        val result = client.graphQL().get()
                .withClassName(DEFAULT_CLASS)
                .withFields(Field.builder().name(METADATA_TEXT_SEGMENT).build(), Field.builder()
                        .name(ADDITIONALS)
                        .fields(
                                Field.builder().name("id").build(),
                                Field.builder().name("certainty").build(),
                                Field.builder().name("vector").build()
                        ).build())
                .withNearVector(NearVectorArgument.builder()
                        .vector(referenceEmbedding.vectorAsList().toArray(new Float[0]))
                        .certainty((float) minCertainty)
                        .build())
                .withLimit(maxResults)
                .run();

        if (result.hasErrors()) {
            System.out.println(result.getError());
            return null;
        }

        val resGetPart = ((Map<String, Map>) result.getResult().getData()).entrySet().stream().findFirst().get().getValue();
        val resItems = ((Map.Entry<String, List<Map<String, ?>>>) resGetPart.entrySet().stream().findFirst().get()).getValue();

        return resItems.stream().map(WeaviateEmbeddingStoreImpl::toEmbeddingMatch).collect(Collectors.toList());
    }

}
