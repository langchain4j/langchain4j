package dev.langchain4j.store.embedding.inmemory;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.segment.TextSegment;

import java.lang.reflect.Type;
import java.util.UUID;

import static com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE;

public class GsonInMemoryEmbeddingStoreJsonCodec implements InMemoryEmbeddingStoreJsonCodec {

    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(LONG_OR_DOUBLE)
            .registerTypeAdapter(UUID.class, new UuidTypeAdapter())
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

    private static class UuidTypeAdapter implements JsonSerializer<UUID>,
            JsonDeserializer<UUID>,
            InstanceCreator<UUID> {

        public JsonElement serialize(UUID src, Type typeOfSrc,
                                     JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        public UUID deserialize(JsonElement json, Type typeOfT,
                                JsonDeserializationContext context)
                throws JsonParseException {
            return UUID.fromString(json.getAsString());
        }

        public UUID createInstance(Type type) {
            return new UUID(0, 0);
        }

        @Override
        public String toString() {
            return UuidTypeAdapter.class.getSimpleName();
        }
    }
}
