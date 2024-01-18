package dev.langchain4j.store.embedding.vearch.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class BulkRequest {

    private List<Map<String, Object>> documents;
}
