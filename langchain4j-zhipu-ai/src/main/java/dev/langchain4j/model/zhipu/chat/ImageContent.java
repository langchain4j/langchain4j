package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.internal.Utils;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageContent implements Content {
    private String type;
    private Image imageUrl;

    public ImageContent(String type, Image imageUrl) {
        this.type = Utils.getOrDefault(type, "image_url");
        this.imageUrl = imageUrl;
    }

    public static ImageContentBuilder builder() {
        return new ImageContentBuilder();
    }


    public static class ImageContentBuilder {
        private String type;
        private Image imageUrl;

        public ImageContentBuilder type(String type) {
            this.type = type;
            return this;
        }

        public ImageContentBuilder imageUrl(Image imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public ImageContent build() {
            return new ImageContent(this.type, this.imageUrl);
        }
    }
}
