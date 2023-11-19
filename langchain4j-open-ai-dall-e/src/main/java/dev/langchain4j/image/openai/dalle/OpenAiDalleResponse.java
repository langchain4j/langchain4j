package dev.langchain4j.image.openai.dalle;

import java.util.List;

public class OpenAiDalleResponse {
    private long created;
    private List<ImageData> data;

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public List<ImageData> getData() {
        return data;
    }

    public void setData(List<ImageData> data) {
        this.data = data;
    }

    public static class ImageData {
        private String url;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
