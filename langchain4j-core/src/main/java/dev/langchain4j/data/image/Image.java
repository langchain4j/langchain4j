package dev.langchain4j.data.image;

public class Image {
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String url;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Image build() {
            return new Image(this);
        }
    }

    private Image(Builder builder) {
        this.url = builder.url;
    }
}
