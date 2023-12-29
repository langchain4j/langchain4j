package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.Part;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;

import java.util.List;

import static com.google.cloud.vertexai.generativeai.preview.PartMaker.fromMimeTypeAndData;
import static dev.langchain4j.internal.Utils.read;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

class PartsMapper {

    static List<Part> map(ChatMessage message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).contents().stream()
                    .map(PartsMapper::map)
                    .collect(toList());
        } else if (message instanceof AiMessage) {
            return singletonList(Part.newBuilder()
                    .setText(((AiMessage) message).text())
                    .build());
        }
        throw new IllegalArgumentException(message.type() + " is not allowed.");
    }

    private static Part map(Content content) {
        if (content instanceof TextContent) {
            return Part.newBuilder()
                    .setText(((TextContent) content).text())
                    .build();
        } else if (content instanceof ImageContent) {
            Image image = ((ImageContent) content).image();
            if (image.url() != null) {
                if (image.url().getScheme().equals("gs")) {
                    return fromMimeTypeAndData(image.mimeType(), image.url());
                } else {
                    return fromMimeTypeAndData(image.mimeType(), read(image.url().toString()));
                }
            }
            return fromMimeTypeAndData(image.mimeType(), Base64.decode(image.base64Data()));
        } else {
            throw new IllegalArgumentException("Unknown content: " + content);
        }
    }
}
