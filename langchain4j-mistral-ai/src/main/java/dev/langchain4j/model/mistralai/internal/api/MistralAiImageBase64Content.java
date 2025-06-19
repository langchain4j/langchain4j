package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiImageBase64Content extends MistralAiMessageContent {

    public String imageUrl;

    public MistralAiImageBase64Content(String imageUrl) {
        super("image_url");
        this.imageUrl = "data:image/jpeg;base64," + imageUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiImageBase64Content that = (MistralAiImageBase64Content) o;
        return Objects.equals(imageUrl, that.imageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), imageUrl);
    }

    @Override
    public String toString() {
        return "MistralAiImageBase64Content{" + "imageUrl="
                + imageUrl + ", type='"
                + type + '\'' + '}';
    }
}
