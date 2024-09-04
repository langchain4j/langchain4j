package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
class GeminiFunctionResponse {
    private String name;
    private Map response;
}
