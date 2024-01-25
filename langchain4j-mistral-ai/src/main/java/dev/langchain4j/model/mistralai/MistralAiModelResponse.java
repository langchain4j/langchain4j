package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralAiModelResponse {

    private String object;
    private List<MistralAiModelCard> data;
}
