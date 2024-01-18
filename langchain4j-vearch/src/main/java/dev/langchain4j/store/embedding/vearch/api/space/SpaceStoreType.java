package dev.langchain4j.store.embedding.vearch.api.space;

import lombok.Getter;

public enum SpaceStoreType {

    MEMORY_ONLY("MemoryOnly"),
    M_MAP("Mmap"),
    ROCKS_DB("RocksDB");

    @Getter
    private final String name;

    SpaceStoreType(String name) {
        this.name = name;
    }
}
