package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
@JsonDeserialize(builder = MistralAiEmbedding.MistralAiEmbeddingBuilder.class)
public class MistralAiEmbedding {

    private String object;
    private List<Float> embedding;
    private Integer index;

    private MistralAiEmbedding(MistralAiEmbeddingBuilder builder) {
        this.object = builder.object;
        this.embedding = builder.embedding;
        this.index = builder.index;
    }

    public String getObject() {
        return this.object;
    }

    public List<Float> getEmbedding() {
        return this.embedding;
    }

    public Integer getIndex() {
        return this.index;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.object);
        hash = 97 * hash + Objects.hashCode(this.embedding);
        hash = 97 * hash + Objects.hashCode(this.index);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MistralAiEmbedding other = (MistralAiEmbedding) obj;
        return Objects.equals(this.object, other.object)
                && Objects.equals(this.embedding, other.embedding)
                && Objects.equals(this.index, other.index);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiEmbedding [", "]")
                .add("object=" + this.getObject())
                .add("embedding=" + this.getEmbedding())
                .add("index=" + this.getIndex())
                .toString();
    }

    public static MistralAiEmbeddingBuilder builder() {
        return new MistralAiEmbeddingBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiEmbeddingBuilder {

        private String object;
        private List<Float> embedding;
        private Integer index;

        private MistralAiEmbeddingBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingBuilder embedding(List<Float> embedding) {
            this.embedding = embedding;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingBuilder index(Integer index) {
            this.index = index;
            return this;
        }

        public MistralAiEmbedding build() {
            return new MistralAiEmbedding(this);
        }
    }
}
