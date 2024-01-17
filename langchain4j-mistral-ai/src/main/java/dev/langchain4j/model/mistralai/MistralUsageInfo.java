package dev.langchain4j.model.mistralai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MistralUsageInfo {

    private Integer promptTokens;
    private Integer totalTokens;
    private Integer completionTokens;

}
