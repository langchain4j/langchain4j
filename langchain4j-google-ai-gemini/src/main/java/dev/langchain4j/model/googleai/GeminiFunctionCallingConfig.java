package dev.langchain4j.model.googleai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class GeminiFunctionCallingConfig {
    private GeminiMode mode;
    private List<String> allowedFunctionNames;
}
