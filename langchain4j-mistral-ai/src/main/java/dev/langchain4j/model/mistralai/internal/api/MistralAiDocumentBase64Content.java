package dev.langchain4j.model.mistralai.internal.api;

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
public class MistralAiDocumentBase64Content extends MistralAiMessageContent {

    public String documentUrl;

    public MistralAiDocumentBase64Content(String documentUrl, String mimeType) {
        super("document_url");
        this.documentUrl = "data:" + mimeType + ";base64," + documentUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MistralAiDocumentBase64Content that = (MistralAiDocumentBase64Content) o;
        return Objects.equals(documentUrl, that.documentUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), documentUrl);
    }

    @Override
    public String toString() {
        return "MistralAiDocumentBase64Content{" + "documentUrl=" + documentUrl + ", type=" + quoted(type) + '}';
    }
}
