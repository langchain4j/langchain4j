package dev.langchain4j.store.embedding.vearch.api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
public class InsertionRequest {

    private String dbName;
    private String spaceName;
    private Map<String, Object> documents;
}
