package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UrlContextMetadata(
        @JsonProperty("urlMetadata") List<UrlMetadata> urlMetadata) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UrlMetadata(
            @JsonProperty("retrievedUrl") String retrievedUrl,
            @JsonProperty("urlRetrievalStatus") String urlRetrievalStatus) {}
}
