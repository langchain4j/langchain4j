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
class MistralAiChatCompletionResponse {

    private String id;
    private String object;
    private Integer created;
    private String model;
    private List<MistralAiChatCompletionChoice> choices;
    private MistralAiUsage usage;
}
