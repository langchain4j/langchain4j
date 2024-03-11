package dev.langchain4j.model.anthropic;

import lombok.AllArgsConstructor;

class AnthropicImageContent {

    private final String type = "image";
    private final Source source;

    AnthropicImageContent(String mediaType, String data) {
        this.source = new Source("base64", mediaType, data);
    }

    @AllArgsConstructor
    static class Source {

        private final String type;
        private final String mediaType;
        private final String data;
    }
}
