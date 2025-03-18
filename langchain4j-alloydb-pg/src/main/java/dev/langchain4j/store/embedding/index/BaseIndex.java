package dev.langchain4j.store.embedding.index;

/**
 * Interface for indexes
 */
public interface BaseIndex {

    /** base index name suffix */
    final String DEFAULT_INDEX_NAME_SUFFIX = "langchainvectorindex";

    /**
     * get  index query options
     * @return  index query options string
     */
    public String getIndexOptions();
}
