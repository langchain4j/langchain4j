package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiEmbedding {

    private String object;
    private List<Float> embedding;
    private Integer index;

    public static class MistralAiEmbeddingBuilder {

        private String object;

        private List<Float> embedding;

        private Integer index;

        MistralAiEmbeddingBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbedding.MistralAiEmbeddingBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbedding.MistralAiEmbeddingBuilder embedding(List<Float> embedding) {
            this.embedding = embedding;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbedding.MistralAiEmbeddingBuilder index(Integer index) {
            this.index = index;
            return this;
        }

        public MistralAiEmbedding build() {
            return new MistralAiEmbedding(this.object, this.embedding, this.index);
        }

        public String toString() {
            return "MistralAiEmbedding.MistralAiEmbeddingBuilder("
                    + "object=" + this.object
                    + ", embedding=" + this.embedding
                    + ", index=" + this.index
                    + ")";
        }
    }

    public static MistralAiEmbedding.MistralAiEmbeddingBuilder builder() {
        return new MistralAiEmbedding.MistralAiEmbeddingBuilder();
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

    public void setObject(String object) {
        this.object = object;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }

    public void setIndex(Integer index) {
        this.index = index;
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
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiEmbedding other = (MistralAiEmbedding) obj;
        return Objects.equals(this.object, other.object)
                && Objects.equals(this.embedding, other.embedding)
                && Objects.equals(this.index, other.index);
    }

    public String toString() {
        return "MistralAiEmbedding("
                + "object=" + this.getObject()
                + ", embedding=" + this.getEmbedding()
                + ", index=" + this.getIndex()
                + ")";
    }

    public MistralAiEmbedding() {
    }

    public MistralAiEmbedding(String object, List<Float> embedding, Integer index) {
        this.object = object;
        this.embedding = embedding;
        this.index = index;
    }
}
