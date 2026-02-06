package dev.langchain4j.model.googleai;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiTool(
        List<GeminiFunctionDeclaration> functionDeclarations,
        GeminiCodeExecution codeExecution,
        @JsonProperty("google_search") GeminiGoogleSearchRetrieval googleSearch,
        GeminiUrlContext urlContext,
        GeminiGoogleMaps googleMaps) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCodeExecution() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiUrlContext() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiGoogleSearchRetrieval() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiGoogleMaps(Boolean enableWidget) { }

}
