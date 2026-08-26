package dev.langchain4j.model.openai.internal.embedding;

import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@JsonDeserialize(builder = Embedding.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Embedding {

    @JsonProperty
    private final List<Float> embedding;

    @JsonProperty
    private final Integer index;

    @JsonCreator
    public Embedding(Builder builder) {
        this.embedding = builder.embedding;
        this.index = builder.index;
    }

    public List<Float> embedding() {
        return embedding;
    }

    public Integer index() {
        return index;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Embedding && equalTo((Embedding) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(Embedding another) {
        return Objects.equals(embedding, another.embedding) && Objects.equals(index, another.index);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(embedding);
        h += (h << 5) + Objects.hashCode(index);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "Embedding{" + "embedding=" + embedding + ", index=" + index + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Builder {

        private List<Float> embedding;
        private Integer index;

        public Builder embedding(List<Float> embedding) {
            if (embedding != null) {
                this.embedding = unmodifiableList(embedding);
            }
            return this;
        }

        /**
         * Paired with the {@code List<Float>} overload above rather than replacing it: callers keep
         * the typed method, while JSON binds through this one because it carries the explicit
         * {@code @JsonProperty}. It has to accept {@code Object} because OpenAI sends the embedding
         * either as an array of numbers or as a Base64 string.
         */
        @JsonProperty("embedding")
        Builder embedding(Object embedding) {
            if (embedding == null) {
                return this;
            }
            if (embedding instanceof String base64) {
                return embedding(decodeBase64(base64));
            }
            if (embedding instanceof List<?> values) {
                List<Float> floats = new ArrayList<>(values.size());
                for (Object value : values) {
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException("Illegal embedding: " + value);
                    }
                    floats.add(number.floatValue());
                }
                return embedding(floats);
            }
            throw new IllegalArgumentException("Illegal embedding: " + embedding);
        }

        /**
         * OpenAI returns the embedding either as an array of numbers or, when
         * {@code encoding_format} is {@code base64}, as little-endian float bytes in Base64.
         */
        private static List<Float> decodeBase64(String base64) {
            byte[] bytes = Base64.getDecoder().decode(base64);
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            List<Float> floats = new ArrayList<>(bytes.length / Float.BYTES);
            while (buffer.remaining() >= Float.BYTES) {
                floats.add(buffer.getFloat());
            }
            return floats;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Embedding build() {
            return new Embedding(this);
        }
    }
}
