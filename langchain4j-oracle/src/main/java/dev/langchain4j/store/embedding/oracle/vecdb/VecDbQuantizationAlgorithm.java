package dev.langchain4j.store.embedding.oracle.vecdb;

/** Advanced quantization algorithms supported by VecDB vector indexes. */
public enum VecDbQuantizationAlgorithm {
    UNIFORM_QUANTIZATION("uniform_quantization");

    private final String databaseValue;

    VecDbQuantizationAlgorithm(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    String databaseValue() {
        return databaseValue;
    }
}
