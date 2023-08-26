package dev.langchain4j.model.vertex;

class VertexAiCompletionInstance {

    private final String prompt;

    public VertexAiCompletionInstance(String prompt) {
        this.prompt = prompt;
    }

    public String prompt() {
        return prompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String prompt;

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public VertexAiCompletionInstance build() {
            return new VertexAiCompletionInstance(prompt);
        }
    }

}
