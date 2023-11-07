package dev.langchain4j.data.image;

public class ImageRequest {
    private String prompt;
    private int n = 1;
    private String size;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String prompt;
        private int n;
        private String size;

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder n(int n) {
            this.n = n;
            return this;
        }

        public Builder size(String size) {
            this.size = size;
            return this;
        }

        public ImageRequest build() {
            return new ImageRequest(this);
        }
    }

    private ImageRequest(Builder builder) {
        this.prompt = builder.prompt;
        this.n = builder.n;
        this.size = builder.size;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "ImageRequest{" +
                "prompt='" + prompt + '\'' +
                ", n=" + n +
                ", size='" + size + '\'' +
                '}';
    }
}
