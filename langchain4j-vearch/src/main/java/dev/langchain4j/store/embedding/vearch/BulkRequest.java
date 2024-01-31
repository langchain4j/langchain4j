package dev.langchain4j.store.embedding.vearch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
class BulkRequest {

    private List<Map<String, Object>> documents;
}
