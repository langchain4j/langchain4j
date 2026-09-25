package dev.langchain4j.store.embedding.oracle.vecdb.enums;

/** Advanced quantization algorithms supported by VecDB vector indexes. */
public enum VecDbQuantizationAlgorithm {
    UNIFORM_QUANTIZATION("uniform_quantization");

    private final String databaseValue;

    VecDbQuantizationAlgorithm(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }
}
