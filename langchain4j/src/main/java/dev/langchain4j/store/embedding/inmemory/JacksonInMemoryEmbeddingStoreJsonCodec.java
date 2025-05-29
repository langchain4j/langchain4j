package dev.langchain4j.store.embedding.inmemory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

@Internal
class JacksonInMemoryEmbeddingStoreJsonCodec implements InMemoryEmbeddingStoreJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .visibility(FIELD, ANY)
            .addMixIn(InMemoryEmbeddingStore.Entry.class, EntryMixIn.class)
            .addMixIn(Embedding.class, EmbeddingMixIn.class)
            .addMixIn(TextSegment.class, TextSegmentMixin.class)
            .build();

    private static final TypeReference<InMemoryEmbeddingStore<TextSegment>> TYPE_REFERENCE = new TypeReference<>() {
    };

    @Override
    public InMemoryEmbeddingStore<TextSegment> fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, TYPE_REFERENCE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(InMemoryEmbeddingStore<?> store) {
        try {
            return OBJECT_MAPPER.writeValueAsString(store);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private abstract static class EntryMixIn<T> {
        @JsonCreator
        EntryMixIn(
                @JsonProperty("id") String id,
                @JsonProperty("embedding") Embedding embedding,
                @JsonProperty("embedded") T embedded) {
        }
    }

    private abstract static class EmbeddingMixIn {
        @JsonCreator
        EmbeddingMixIn(@JsonProperty("vector") float[] vector) {
        }

        @JsonProperty("vector")
        abstract float[] vector();
    }

    private abstract static class TextSegmentMixin {

        @JsonCreator
        public TextSegmentMixin(@JsonProperty("text") String text, @JsonProperty("metadata") Metadata metadata) {

        }
    }
}
