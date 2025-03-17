package dev.langchain4j.model.dashscope;

public enum WanxImageRefMode {
    REPAINT("repaint"),
    REFONLY("refonly");

    private final String mode;

    WanxImageRefMode(String mode) {
        this.mode = mode;
    }

    @Override
    public String toString() {
        return mode;
    }
}
