package dev.langchain4j.model.openai.internal.chat;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;

import java.util.Objects;

@JsonDeserialize(builder = VideoUrl.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoUrl {

    @JsonProperty
    private final String url;

    @JsonCreator
    public VideoUrl(Builder builder) {
        this.url = builder.url;
    }

    public String getUrl() {
        return url;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof VideoUrl && equalTo((VideoUrl) another);
    }

    @JacocoIgnoreCoverageGenerated
    private boolean equalTo(VideoUrl another) {
        return Objects.equals(url, another.url);
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(url);
        return h;
    }

    @Override
    @JacocoIgnoreCoverageGenerated
    public String toString() {
        return "VideoUrl{" + "url=" + url + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static final class Builder {

        private String url;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public VideoUrl build() {
            return new VideoUrl(this);
        }
    }
}
