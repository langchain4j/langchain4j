package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SpaceStoreType {

    @JsonProperty("MemoryOnly")
    MEMORY_ONLY,
    @JsonProperty("Mmap")
    M_MAP,
    @JsonProperty("RocksDB")
    ROCKS_DB
}
