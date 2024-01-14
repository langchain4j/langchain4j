package dev.langchain4j.store.embedding.vearch.api.space;

public enum SpaceStoreType {

    MEMORY_ONLY("MemoryOnly"),
    M_MAP("Mmap"),
    ROCKS_DB("RocksDB");

    private final String name;

    SpaceStoreType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
