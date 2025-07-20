package dev.langchain4j.store.embedding.vespa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record QueryResponse(RootNode root) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RootNode(List<Record> children) {}
}
