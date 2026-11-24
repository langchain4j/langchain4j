package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.ImageContent;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Every download of an image in this module goes through {@link #base64Image(Image, MediaFetcher)}. The asynchronous
 * and streaming paths download the URLs of {@link #mediaUrls(List)} first, without blocking.
 */
@Internal
class ImageUtils {

    static List<String> base64EncodeImageList(List<ImageContent> contentList, MediaFetcher mediaFetcher) {
        return contentList.stream()
                .map(ImageContent::image)
                .map(image -> base64Image(image, mediaFetcher))
                .collect(Collectors.toList());
    }

    static String base64Image(Image image, MediaFetcher mediaFetcher) {
        if (!isNullOrEmpty(image.base64Data())) {
            return image.base64Data();
        }
        return Base64.getEncoder().encodeToString(mediaFetcher.fetch(MediaFetchRequest.from(image.url())).bytes());
    }

    /**
     * Returns the URLs of the images that {@link #base64Image(Image, MediaFetcher)} would download.
     */
    static List<URI> mediaUrls(List<Image> images) {
        List<URI> urls = new ArrayList<>();
        for (Image image : images) {
            if (isNullOrEmpty(image.base64Data()) && image.url() != null) {
                urls.add(image.url());
            }
        }
        return urls;
    }
}
