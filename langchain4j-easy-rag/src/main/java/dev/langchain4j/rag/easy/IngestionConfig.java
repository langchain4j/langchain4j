package dev.langchain4j.rag.easy;

public class IngestionConfig {

    private final Integer segmentSize;
    private final Integer segmentOverlap;

    public IngestionConfig(Integer segmentSize, Integer segmentOverlap) {
        this.segmentSize = segmentSize;
        this.segmentOverlap = segmentOverlap;
    }

    public Integer segmentSize() {
        return segmentSize;
    }

    public Integer segmentOverlap() {
        return segmentOverlap;
    }
}