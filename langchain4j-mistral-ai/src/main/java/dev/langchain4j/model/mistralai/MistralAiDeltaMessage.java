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
public class MistralAiDeltaMessage {

    private MistralAiRole role;
    private String content;
    private List<MistralAiToolCall> toolCalls;
}
