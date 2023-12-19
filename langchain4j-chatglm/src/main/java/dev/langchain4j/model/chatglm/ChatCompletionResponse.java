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
class ChatCompletionResponse {

    private String response;
    private List<List<String>> history;
    private Integer status;
    private String time;
}
