package dev.langchain4j.model.zhipu.image;

public enum ImageModelName {

    COGVIEW_3("cogview-3"),
    ;

    private final String value;

    ImageModelName(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
