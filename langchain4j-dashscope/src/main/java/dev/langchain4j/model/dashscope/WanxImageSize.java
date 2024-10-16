package dev.langchain4j.model.dashscope;

public enum WanxImageSize {
    SIZE_1024_1024("1024*1024"),
    SIZE_720_1280("720*1280"),
    SIZE_1280_720("1280*720");

    private final String size;

    WanxImageSize(String size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return size;
    }
}
