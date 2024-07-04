package dev.langchain4j.model.sparkdesk.client.image;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Ratio {
    WIDTH_512_HEIGHT_512(512, 512),
    WIDTH_640_HEIGHT_360(640, 360),
    WIDTH_640_HEIGHT_480(640, 480),
    WIDTH_640_HEIGHT_640(640, 640),
    WIDTH_680_HEIGHT_512(680, 512),
    WIDTH_512_HEIGHT_680(512, 680),
    WIDTH_768_HEIGHT_768(768, 768),
    WIDTH_720_HEIGHT_1280(720, 1280),
    WIDTH_1280_HEIGHT_720(1280, 720),
    WIDTH_1024_HEIGHT1024(1024, 1024);
    private int width;
    private int height;
}
