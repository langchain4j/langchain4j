package dev.langchain4j.store.embedding.vearch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SpaceStoreParam {

    /**
     * It means you will use so much memory, the excess will be kept to disk. For MemoryOnly, this parameter is invalid.
     */
    private Integer cacheSize;
    private CompressRate compress;

    @Getter
    @Setter
    @Builder
    public static class CompressRate {

        private Integer rate;
    }
}
