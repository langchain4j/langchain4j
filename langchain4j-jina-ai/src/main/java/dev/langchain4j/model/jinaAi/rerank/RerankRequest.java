package dev.langchain4j.model.jinaAi.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
class RerankRequest {

    private String model;
    private String query;
    private List<String> documents;
}
