package dev.langchain4j.store.embedding.vearch.api.space;

public class SpaceStoreParam {

    private Integer cacheSize;
    private CompressRate compress;

    public static class CompressRate {

        private Integer rate;

        public Integer getRate() {
            return rate;
        }

        public void setRate(Integer rate) {
            this.rate = rate;
        }
    }

    public Integer getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(Integer cacheSize) {
        this.cacheSize = cacheSize;
    }

    public CompressRate getCompress() {
        return compress;
    }

    public void setCompress(CompressRate compress) {
        this.compress = compress;
    }
}
