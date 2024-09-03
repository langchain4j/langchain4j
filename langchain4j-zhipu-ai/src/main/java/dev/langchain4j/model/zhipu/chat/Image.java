package dev.langchain4j.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Image {
    private String url;

    public Image(String url) {
        this.url = url;
    }

    public static ImageBuilder builder() {
        return new ImageBuilder();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public static class ImageBuilder {
        private String url;

        public ImageBuilder url(String url) {
            this.url = url;
            return this;
        }

        public Image build() {
            return new Image(this.url);
        }

        public String toString() {
            return "Image.ImageBuilder(url=" + this.url + ")";
        }
    }
}
