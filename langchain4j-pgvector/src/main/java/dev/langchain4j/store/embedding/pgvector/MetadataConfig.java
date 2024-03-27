package dev.langchain4j.store.embedding.pgvector;

import java.util.List;
import java.util.Optional;

public interface MetadataConfig {
    String type();
    List<String> definition();
    Optional<List<String>> indexes();
    String indexType();
}
