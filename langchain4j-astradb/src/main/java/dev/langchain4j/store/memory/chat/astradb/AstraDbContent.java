package dev.langchain4j.store.memory.chat.astradb;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;

/**
 * Content interface makes the serialization more complex than it should be.
 * This flatten structure is a workaround to simplify the serialization.
 */
@Data
@NoArgsConstructor
public class AstraDbContent implements Content  {

    private String text;

    private ContentType type;

    private Image image;

    private ImageContent.DetailLevel detailLevel;

    public AstraDbContent(Content c) {
        switch(c.type()) {
            case TEXT:
                this.text = ((dev.langchain4j.data.message.TextContent) c).text();
                this.type = ContentType.TEXT;
                break;
            case IMAGE:
                this.image = ((ImageContent) c).image();
                this.detailLevel = ((ImageContent) c).detailLevel();
                this.type = ContentType.IMAGE;
                break;
        }
    }

    public Content asContent() {
        switch (type) {
            case TEXT:
                return new dev.langchain4j.data.message.TextContent(text);
            case IMAGE:
                return new ImageContent(image, detailLevel);
            default:
                throw new IllegalStateException("Unknown content type: " + type);
        }
    }

    @Override
    public ContentType type() {
        return type;
    }

}
