package dev.langchain4j.store.embedding.inmemory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.segment.TextSegment;

import java.lang.reflect.Type;

import static com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE;

public class GsonInMemoryEmbeddingStoreJsonCodec implements InMemoryEmbeddingStoreJsonCodec {

    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(LONG_OR_DOUBLE)
            .create();

    private static final Type TYPE = new TypeToken<InMemoryEmbeddingStore<TextSegment>>() {
    }.getType();

    @Override
    public InMemoryEmbeddingStore<TextSegment> fromJson(String json) {
        return GSON.fromJson(json, TYPE);
    }

    @Override
    public String toJson(InMemoryEmbeddingStore<?> store) {
        return GSON.toJson(store);
    }

}
