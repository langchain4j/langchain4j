package dev.langchain4j.model.voyage;

public enum VoyageEmbeddingModelName {

    VOYAGE_3("voyage-3", 1024),
    VOYAGE_3_LITE("voyage-3-lite", 512),

    VOYAGE_FINANCE_2("voyage-finance-2", 1024),

    VOYAGE_MULTILINGUAL_2("voyage-multilingual-2", 1024),

    VOYAGE_LAW_2("voyage-law-2", 1024),

    VOYAGE_CODE_2("voyage-code-2", 1536);

    private final String stringValue;
    private final Integer dimension;

    VoyageEmbeddingModelName(String stringValue, Integer dimension) {
        this.stringValue = stringValue;
        this.dimension = dimension;
    }

    @Override
    public String toString() {
        return stringValue;
    }

    public Integer dimension() {
        return dimension;
    }
}
