package dev.langchain4j.model.zhipu.image;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.List;

@Builder
@lombok.Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponse {
    private Long created;
    private List<Data> data;
}
