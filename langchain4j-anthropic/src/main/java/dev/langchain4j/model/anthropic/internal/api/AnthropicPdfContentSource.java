package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicPdfContentSource {

    public String type;
    public String mediaType;
    public String data;
    public String url;

    public AnthropicPdfContentSource(String type, String mediaType, String data, String url) {
        this.type = type;
        this.mediaType = mediaType;
        this.data = data;
        this.url = url;
    }

    public AnthropicPdfContentSource(String type, String mediaType, String data) {
        this(type, mediaType, data, null);
    }

    public static AnthropicPdfContentSource fromBase64(String mediaType, String data) {
        return new AnthropicPdfContentSource("base64", mediaType, data, null);
    }

    public static AnthropicPdfContentSource fromUrl(String url) {
        return new AnthropicPdfContentSource("url", null, null, url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnthropicPdfContentSource that = (AnthropicPdfContentSource) o;
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
        return "AnthropicPdfContentSource {" + " type = "
                + quoted(type) + ", mediaType = "
                + quoted(mediaType) + ", data = "
                + quoted(data) + ", url = "
                + quoted(url) + " }";
    }
}
