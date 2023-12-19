package dev.langchain4j.model.chatglm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ChatCompletionRequest {

    private String prompt;
    private Double temperature;
    private Double topP;
    private Integer maxLength;
    private List<List<String>> history;
}
