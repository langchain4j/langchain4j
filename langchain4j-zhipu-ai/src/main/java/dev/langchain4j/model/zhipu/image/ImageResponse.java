package dev.langchain4j.model.zhipu.image;

import java.util.List;

public class ImageResponse {
    private Long created;
    private List<Data> data;

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public List<Data> getData() {
        return data;
    }

    public void setData(List<Data> data) {
        this.data = data;
    }
}
