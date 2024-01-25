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
class MistralEmbeddingResponse {

    private String id;
    private String object;
    private String model;
    private List<MistralEmbeddingObject> data;
    private MistralUsageInfo usage;

}
