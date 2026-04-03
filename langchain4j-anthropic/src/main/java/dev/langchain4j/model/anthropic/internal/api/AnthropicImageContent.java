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
public class AnthropicImageContent extends AnthropicMessageContent {

    public AnthropicImageContentSource source;

    public AnthropicImageContent(AnthropicImageContentSource source) {
        super("image");
        this.source = source;
    }

    public AnthropicImageContent(String mediaType, String data) {
        super("image");
        this.source = new AnthropicImageContentSource("base64", mediaType, data);
    }

    public static AnthropicImageContent fromBase64(String mediaType, String data) {
        return new AnthropicImageContent(AnthropicImageContentSource.fromBase64(mediaType, data));
    }

    public static AnthropicImageContent fromUrl(String url) {
        return new AnthropicImageContent(AnthropicImageContentSource.fromUrl(url));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicImageContent that = (AnthropicImageContent) o;
        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source);
    }

    @Override
    public String toString() {
        return "AnthropicImageContent{" + "source="
                + source + ", type='"
                + type + '\'' + ", cacheControl="
                + cacheControl + '}';
    }
}
