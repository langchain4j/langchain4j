package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UrlContextMetadata(List<UrlMetadata> urlMetadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UrlMetadata(String retrievedUrl, String urlRetrievalStatus) {}
}
