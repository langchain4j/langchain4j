package dev.langchain4j.model.ollama;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.internal.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

class ImageUtils {

    private static final List<String> SUPPORTED_URL_SCHEMES = Arrays.asList("http", "https", "file");

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
            if (SUPPORTED_URL_SCHEMES.contains(image.url().getScheme())) {
                return image.url().getScheme().startsWith("http") ? httpScheme(image) : fileScheme(image);
            } else {
                throw new RuntimeException("ollama integration only supports http/https and file urls. unsupported url scheme: " + image.url().getScheme());
            }
        }
    }

    private static String httpScheme(Image image) {
        byte[] imageBytes = Utils.readBytes(image.url().toString());
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private static String fileScheme(Image image) {
        byte[] fileBytes = readAllBytes(Paths.get(image.url()));
        return Base64.getEncoder().encodeToString(fileBytes);

    }

    private static byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("cant read file", e);
        }
    }
}