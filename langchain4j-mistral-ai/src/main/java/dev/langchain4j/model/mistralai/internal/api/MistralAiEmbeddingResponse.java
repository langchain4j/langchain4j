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
@JsonDeserialize(builder = MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder.class)
public class MistralAiEmbeddingResponse {
    private String id;
    private String object;
    private String model;
    private List<MistralAiEmbedding> data;
    private MistralAiUsage usage;

    private MistralAiEmbeddingResponse(MistralAiEmbeddingResponseBuilder builder) {
        this.id = builder.id;
        this.object = builder.object;
        this.model = builder.model;
        this.data = builder.data;
        this.usage = builder.usage;
    }

    public String getId() {
        return this.id;
    }

    public String getObject() {
        return this.object;
    }

    public String getModel() {
        return this.model;
    }

    public List<MistralAiEmbedding> getData() {
        return this.data;
    }

    public MistralAiUsage getUsage() {
        return this.usage;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.id);
        hash = 53 * hash + Objects.hashCode(this.object);
        hash = 53 * hash + Objects.hashCode(this.model);
        hash = 53 * hash + Objects.hashCode(this.data);
        hash = 53 * hash + Objects.hashCode(this.usage);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiEmbeddingResponse other = (MistralAiEmbeddingResponse) obj;
        return Objects.equals(this.id, other.id)
                && Objects.equals(this.object, other.object)
                && Objects.equals(this.model, other.model)
                && Objects.equals(this.data, other.data)
                && Objects.equals(this.usage, other.usage);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiEmbeddingResponse [", "]")
                .add("id=" + this.getId())
                .add("object=" + this.getObject())
                .add("model=" + this.getModel())
                .add("data=" + this.getData())
                .add("usage=" + this.getUsage())
                .toString();
    }

    public static MistralAiEmbeddingResponseBuilder builder() {
        return new MistralAiEmbeddingResponseBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiEmbeddingResponseBuilder {

        private String id;
        private String object;
        private String model;
        private List<MistralAiEmbedding> data;
        private MistralAiUsage usage;

        private MistralAiEmbeddingResponseBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponseBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponseBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponseBuilder data(List<MistralAiEmbedding> data) {
            this.data = data;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponseBuilder usage(MistralAiUsage usage) {
            this.usage = usage;
            return this;
        }

        public MistralAiEmbeddingResponse build() {
            return new MistralAiEmbeddingResponse(this);
        }
    }
}
