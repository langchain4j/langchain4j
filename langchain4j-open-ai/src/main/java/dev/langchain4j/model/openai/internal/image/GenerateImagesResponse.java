package dev.langchain4j.model.openai.internal.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Represents the response from the OpenAI DALL·E API when generating images.
 * Find description of parameters <a href="https://platform.openai.com/docs/api-reference/images/object">here</a>.
 */
@JsonDeserialize(builder = GenerateImagesResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GenerateImagesResponse {

    @JsonProperty
    private final List<ImageData> data;

    public GenerateImagesResponse(Builder builder) {
        this.data = builder.data;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ImageData> data() {
        return data;
    }

    @Override
    public String toString() {
        return "GenerateImagesResponse{" + "data=" + data + '}';
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        if (another == null || getClass() != another.getClass()) return false;
        GenerateImagesResponse anotherGenerateImagesResponse = (GenerateImagesResponse) another;
        return Objects.equals(data, anotherGenerateImagesResponse.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private List<ImageData> data;

        public Builder data(List<ImageData> data) {
            this.data = data;
            return this;
        }

        public GenerateImagesResponse build() {
            return new GenerateImagesResponse(this);
        }
    }
}
