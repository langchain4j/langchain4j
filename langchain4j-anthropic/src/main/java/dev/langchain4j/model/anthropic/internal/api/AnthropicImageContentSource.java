package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicImageContentSource {

    public String type;
    public String mediaType;
    public String data;
    public String url;

    public AnthropicImageContentSource(String type, String mediaType, String data, String url) {
        this.type = type;
        this.mediaType = mediaType;
        this.data = data;
        this.url = url;
    }

    public AnthropicImageContentSource(String type, String mediaType, String data) {
        this.type = type;
        this.mediaType = mediaType;
        this.data = data;
    }

    public static AnthropicImageContentSource fromBase64(String mediaType, String data) {
        return new AnthropicImageContentSource("base64", mediaType, data, null);
    }

    public static AnthropicImageContentSource fromUrl(String url) {
        return new AnthropicImageContentSource("url", null, null, url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicImageContentSource that = (AnthropicImageContentSource) o;
        return Objects.equals(type, that.type)
                && Objects.equals(mediaType, that.mediaType)
                && Objects.equals(data, that.data)
                && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, mediaType, data, url);
    }

    @Override
    public String toString() {
        return "AnthropicImageContentSource{" + "type='"
                + type + '\'' + ", mediaType='"
                + mediaType + '\'' + ", data='"
                + data + '\'' + ", url='"
                + url + '\'' + '}';
    }
}
