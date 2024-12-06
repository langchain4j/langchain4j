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
public class MistralAiEmbeddingResponse {

    private String id;
    private String object;
    private String model;
    private List<MistralAiEmbedding> data;
    private MistralAiUsage usage;

    public static class MistralAiEmbeddingResponseBuilder {

        private String id;

        private String object;

        private String model;

        private List<MistralAiEmbedding> data;

        private MistralAiUsage usage;

        MistralAiEmbeddingResponseBuilder() {
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder data(List<MistralAiEmbedding> data) {
            this.data = data;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder usage(MistralAiUsage usage) {
            this.usage = usage;
            return this;
        }

        public MistralAiEmbeddingResponse build() {
            return new MistralAiEmbeddingResponse(this.id, this.object, this.model, this.data, this.usage);
        }

        public String toString() {
            return "MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder("
                    + "id=" + this.id
                    + ", object=" + this.object
                    + ", model=" + this.model + ", data="
                    + this.data + ", usage="
                    + this.usage
                    + ")";
        }
    }

    public static MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder builder() {
        return new MistralAiEmbeddingResponse.MistralAiEmbeddingResponseBuilder();
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

    public void setId(String id) {
        this.id = id;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setData(List<MistralAiEmbedding> data) {
        this.data = data;
    }

    public void setUsage(MistralAiUsage usage) {
        this.usage = usage;
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

    public String toString() {
        return "MistralAiEmbeddingResponse("
                + "id=" + this.getId()
                + ", object=" + this.getObject()
                + ", model=" + this.getModel()
                + ", data=" + this.getData()
                + ", usage=" + this.getUsage()
                + ")";
    }

    public MistralAiEmbeddingResponse() {
    }

    public MistralAiEmbeddingResponse(String id, String object, String model, List<MistralAiEmbedding> data, MistralAiUsage usage) {
        this.id = id;
        this.object = object;
        this.model = model;
        this.data = data;
        this.usage = usage;
    }
}
