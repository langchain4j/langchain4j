package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
class GeminiFunctionCall {
    private String name;
    private Map<String, Object> args;
}
