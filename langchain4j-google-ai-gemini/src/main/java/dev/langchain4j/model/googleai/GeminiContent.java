package dev.langchain4j.model.googleai;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
class GeminiContent {
    private List<GeminiPart> parts;
    private String role;

    public GeminiContent(String role) {
        this.parts = new ArrayList<>();
        this.role = role;
    }

    void addPart(GeminiPart part) {
        this.parts.add(part);
    }
}
