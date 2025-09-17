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

    private AnthropicImageContentSource() {}

    public AnthropicImageContentSource(String type, String mediaType, String data) {
        this.type = type;
        this.mediaType = mediaType;
        this.data = data;
    }

    public static AnthropicImageContentSource forBase64(String mediaType, String data) {
        AnthropicImageContentSource source = new AnthropicImageContentSource();
        source.type = "base64";
        source.mediaType = mediaType;
        source.data = data;
        return source;
    }

    public static AnthropicImageContentSource forUrl(String url) {
        AnthropicImageContentSource source = new AnthropicImageContentSource();
        source.type = "url";
        source.url = url;
        return source;
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
