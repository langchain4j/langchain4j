package dev.langchain4j.model.huggingface;

class Options {

    private final boolean waitForModel;

    Options(boolean waitForModel) {
        this.waitForModel = waitForModel;
    }
}
