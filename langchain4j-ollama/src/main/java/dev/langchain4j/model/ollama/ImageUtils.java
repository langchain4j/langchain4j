package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.internal.MediaPrefetcher;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Every download of an image in this module goes through {@link #base64Image(Image, MediaFetcher)} or
 * {@link #prefetch(MediaFetcher, List)}.
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
        return Base64.getEncoder().encodeToString(mediaFetcher.fetch(image.url()).bytes());
    }

    /**
     * Downloads, with {@link MediaFetcher#fetchAsync(URI)}, the images that {@link #base64Image(Image, MediaFetcher)}
     * would download.
     *
     * @return a future of a fetcher that returns the downloaded images; it never throws.
     */
    static CompletableFuture<MediaFetcher> prefetch(MediaFetcher mediaFetcher, List<Image> images) {
        List<URI> urls = new ArrayList<>();
        for (Image image : images) {
            if (isNullOrEmpty(image.base64Data()) && image.url() != null) {
                urls.add(image.url());
            }
        }
        return MediaPrefetcher.prefetch(mediaFetcher, urls);
    }
}
