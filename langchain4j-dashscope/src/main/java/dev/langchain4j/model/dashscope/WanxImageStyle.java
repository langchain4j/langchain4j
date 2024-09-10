package dev.langchain4j.model.dashscope;

public enum WanxImageStyle {
    PHOTOGRAPHY("<photography>"),
    PORTRAIT("<portrait>"),
    CARTOON_3D("<3d cartoon>"),
    ANIME("<anime>"),
    OIL_PAINTING("<oil painting>"),
    WATERCOLOR("<watercolor>"),
    SKETCH("<sketch>"),
    CHINESE_PAINTING("<chinese painting>"),
    FLAT_ILLUSTRATION("<flat illustration>"),
    AUTO("<auto>");

    private final String style;

    WanxImageStyle(String style) {
        this.style = style;
    }

    @Override
    public String toString() {
        return style;
    }
}
