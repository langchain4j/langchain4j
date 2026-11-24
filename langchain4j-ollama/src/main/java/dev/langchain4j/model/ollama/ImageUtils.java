package dev.langchain4j.model.ollama;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.UnsupportedFeatureException;

import java.util.List;
import java.util.stream.Collectors;

@Internal
class ImageUtils {

    static final String IMAGE_URL_NOT_SUPPORTED = "Ollama does not support images given by URL; it needs the image content."
            + " Create the content from a file or from its bytes, for example ImageContent.from(Path, String)"
            + " or ImageContent.from(String base64Data, String mimeType)";

    static List<String> base64EncodeImageList(List<ImageContent> contentList) {
        return contentList.stream()
                .map(ImageContent::image)
                .map(ImageUtils::base64Image)
                .collect(Collectors.toList());
    }

    static String base64Image(Image image) {

        if (image.base64Data() != null && !image.base64Data().isEmpty()) {
            return image.base64Data();
        } else {
            throw new UnsupportedFeatureException(IMAGE_URL_NOT_SUPPORTED);
        }
    }
}
