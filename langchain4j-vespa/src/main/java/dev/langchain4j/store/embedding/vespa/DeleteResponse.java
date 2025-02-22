package dev.langchain4j.store.embedding.vespa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeleteResponse(String pathId, Long documentCount) {}
