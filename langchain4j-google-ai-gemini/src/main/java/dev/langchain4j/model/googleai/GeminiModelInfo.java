package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiModelInfo(
        String name,
        String baseModelId,
        String version,
        String displayName,
        String description,
        Integer inputTokenLimit,
        Integer outputTokenLimit,
        List<String> supportedGenerationMethods,
        Double temperature,
        Double maxTemperature,
        Double topP,
        Integer topK) {}
