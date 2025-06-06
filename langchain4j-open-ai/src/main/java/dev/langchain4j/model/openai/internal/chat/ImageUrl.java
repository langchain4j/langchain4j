package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

@JsonDeserialize(builder = ImageUrl.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ImageUrl {

    @JsonProperty
    private final String url;
    @JsonProperty
    private final ImageDetail detail;

    public ImageUrl(Builder builder) {
        this.url = builder.url;
        this.detail = builder.detail;
    }

    public String getUrl() {
        return url;
    }

    public ImageDetail getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ImageUrl
                && equalTo((ImageUrl) another);
    }

    private boolean equalTo(ImageUrl another) {
        return Objects.equals(url, another.url)
                && Objects.equals(detail, another.detail);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(url);
        h += (h << 5) + Objects.hashCode(detail);
        return h;
    }

    @Override
    public String toString() {
        return "ImageUrl{" +
                "url=" + url +
                ", detail=" + detail +
                "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {

        private String url;
        private ImageDetail detail;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder detail(ImageDetail detail) {
            this.detail = detail;
            return this;
        }

        public ImageUrl build() {
            return new ImageUrl(this);
        }
    }
}
