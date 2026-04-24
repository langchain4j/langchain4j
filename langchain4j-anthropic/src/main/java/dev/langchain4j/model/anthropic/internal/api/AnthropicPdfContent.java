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
public class AnthropicPdfContent extends AnthropicMessageContent {

    public AnthropicPdfContentSource source;

    public AnthropicPdfContent(AnthropicPdfContentSource source) {
        super("document");
        this.source = source;
    }

    public AnthropicPdfContent(String mediaType, String base64Data) {
        super("document");
        this.source = new AnthropicPdfContentSource("base64", mediaType, base64Data);
    }

    public AnthropicPdfContent(String base64Data) {
        this("application/pdf", base64Data);
    }

    public static AnthropicPdfContent fromBase64(String mediaType, String data) {
        return new AnthropicPdfContent(AnthropicPdfContentSource.fromBase64(mediaType, data));
    }

    public static AnthropicPdfContent fromUrl(String url) {
        return new AnthropicPdfContent(AnthropicPdfContentSource.fromUrl(url));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AnthropicPdfContent that = (AnthropicPdfContent) o;
        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source);
    }

    @Override
    public String toString() {
        return "AnthropicPdfContent{" + "source=" + source + '}';
    }
}
