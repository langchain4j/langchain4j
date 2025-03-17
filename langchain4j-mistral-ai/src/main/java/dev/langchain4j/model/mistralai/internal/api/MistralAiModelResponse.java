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
@JsonDeserialize(builder = MistralAiModelResponse.MistralAiModelResponseBuilder.class)
public class MistralAiModelResponse {
    private String object;
    private List<MistralAiModelCard> data;

    private MistralAiModelResponse(MistralAiModelResponseBuilder builder) {
        this.object = builder.object;
        this.data = builder.data;
    }

    public String getObject() {
        return this.object;
    }

    public List<MistralAiModelCard> getData() {
        return this.data;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.object);
        hash = 47 * hash + Objects.hashCode(this.data);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        final MistralAiModelResponse other = (MistralAiModelResponse) obj;
        return Objects.equals(this.object, other.object) && Objects.equals(this.data, other.data);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "MistralAiModelResponse [", "]")
                .add("object=" + this.getObject())
                .add("data=" + this.getData())
                .toString();
    }

    public static MistralAiModelResponse.MistralAiModelResponseBuilder builder() {
        return new MistralAiModelResponse.MistralAiModelResponseBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class MistralAiModelResponseBuilder {

        private String object;

        private List<MistralAiModelCard> data;

        private MistralAiModelResponseBuilder() {}

        /**
         * @return {@code this}.
         */
        public MistralAiModelResponseBuilder object(String object) {
            this.object = object;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public MistralAiModelResponseBuilder data(List<MistralAiModelCard> data) {
            this.data = data;
            return this;
        }

        public MistralAiModelResponse build() {
            return new MistralAiModelResponse(this);
        }
    }
}
