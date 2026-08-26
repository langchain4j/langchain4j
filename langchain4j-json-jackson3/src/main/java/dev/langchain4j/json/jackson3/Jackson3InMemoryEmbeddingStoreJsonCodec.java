package dev.langchain4j.json.jackson3;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodec;
import java.io.InputStream;
import java.io.OutputStream;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 twin of the default in-memory embedding store codec.
 *
 * <p>Every annotation on the mixins below comes from the shared {@code jackson-annotations}
 * artifact, so they are identical to the Jackson 2 version.
 */
public class Jackson3InMemoryEmbeddingStoreJsonCodec implements InMemoryEmbeddingStoreJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder())
            .changeDefaultVisibility(vc -> vc.withVisibility(FIELD, ANY))
            .addMixIn(InMemoryEmbeddingStore.Entry.class, EntryMixIn.class)
            .addMixIn(Embedding.class, EmbeddingMixIn.class)
            .addMixIn(TextSegment.class, TextSegmentMixin.class)
            .build();

    private static final TypeReference<InMemoryEmbeddingStore<TextSegment>> TYPE_REFERENCE = new TypeReference<>() {};

    @Override
    public InMemoryEmbeddingStore<TextSegment> fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, TYPE_REFERENCE);
        } catch (JacksonException e) {
            throw new JsonReadException(e);
        }
    }

    @Override
    public String toJson(InMemoryEmbeddingStore<?> store) {
        try {
            return OBJECT_MAPPER.writeValueAsString(store);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
    }

    @Override
    public void toJson(OutputStream outputStream, InMemoryEmbeddingStore<?> store) {
        try {
            OBJECT_MAPPER.writeValue(outputStream, store);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
    }

    @Override
    public InMemoryEmbeddingStore<TextSegment> fromJson(InputStream inputStream) {
        try {
            return OBJECT_MAPPER.readValue(inputStream, TYPE_REFERENCE);
        } catch (JacksonException e) {
            throw new JsonReadException(e);
        }
    }

    private abstract static class EntryMixIn<T> {
        @JsonCreator
        EntryMixIn(
                @JsonProperty("id") String id,
                @JsonProperty("embedding") Embedding embedding,
                @JsonProperty("embedded") T embedded) {}
    }

    private abstract static class EmbeddingMixIn {
        @JsonCreator
        EmbeddingMixIn(@JsonProperty("vector") float[] vector) {}

        @JsonProperty("vector")
        abstract float[] vector();
    }

    private abstract static class TextSegmentMixin {
        @JsonCreator
        public TextSegmentMixin(@JsonProperty("text") String text, @JsonProperty("metadata") Metadata metadata) {}
    }
}
