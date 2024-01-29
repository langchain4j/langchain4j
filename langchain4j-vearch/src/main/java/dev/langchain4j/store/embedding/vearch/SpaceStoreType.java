package dev.langchain4j.store.embedding.vearch;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

public enum SpaceStoreType {

    @SerializedName("MemoryOnly")
    MEMORY_ONLY,
    @SerializedName("Mmap")
    M_MAP,
    @SerializedName("RocksDB")
    ROCKS_DB
}
