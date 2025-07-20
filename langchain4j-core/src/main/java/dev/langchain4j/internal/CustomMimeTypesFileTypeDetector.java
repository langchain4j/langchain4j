package dev.langchain4j.internal;

import dev.langchain4j.Internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.spi.FileTypeDetector;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 *     Utility class to guess the mime-type of a file from its path or URI.
 * </p>
 * <p>
 *     As JDK's built-in doesn't recognize all possible mime-types
 *     in particular for newer file types like YAML,
 *     or returns odd mime-types like for the TypeScript <code>.ts</code> extension,
 *     this class allows you to define your own extension to mime-type mapping,
 *     in addition to providing a few extra ones built-in.
 * </p>
 * <p>
 *     When the mime-type can't be found in those mappings, or from JDK's built-in,
 *     it tries, as a fallback, to use <code>URLConnection</code>'s capability,
 *     but it's slower as it needs to connect to the underlying URL.
 * </p>
 */
@Internal
public class CustomMimeTypesFileTypeDetector extends FileTypeDetector {

    private static final Map<String, String> defaultMappings = new HashMap<>();
    static {
        defaultMappings.put("pdf",   "application/pdf");
        defaultMappings.put("yml",   "text/yaml");
        defaultMappings.put("yaml",  "text/yaml");
        defaultMappings.put("json",  "application/json");
        defaultMappings.put("js",    "text/javascript");
        defaultMappings.put("mjs",   "text/javascript");
        defaultMappings.put("ts",    "text/x.typescript");
        defaultMappings.put("txt",   "text/plain");
        defaultMappings.put("xml",   "application/xml");
        defaultMappings.put("svg",   "image/svg+xml");
        defaultMappings.put("xhtml", "application/xhtml+xml");
        defaultMappings.put("html",  "text/html");
        defaultMappings.put("htm",   "text/html");
        defaultMappings.put("css",   "text/css");
        defaultMappings.put("csv",   "text/csv");
        defaultMappings.put("tsv",   "text/tsv");
        defaultMappings.put("md",    "text/x-markdown");

        // Mime types from image requirements for Vertex AI Gemini
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/image-understanding#image-requirements
        defaultMappings.put("avif", "image/avif");
        defaultMappings.put("bmp",  "image/bmp");
        defaultMappings.put("gif",  "image/gif");
        defaultMappings.put("jpe",  "image/jpeg");
        defaultMappings.put("jpeg", "image/jpeg");
        defaultMappings.put("jpg",  "image/jpeg");
        defaultMappings.put("png",  "image/png");
        defaultMappings.put("tif",  "image/tiff");
        defaultMappings.put("tiff", "image/tiff");
        defaultMappings.put("webp", "image/webp");

        // Mime types from audio requirements for Vertex AI Gemini
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/audio-understanding
        defaultMappings.put("mp3",  "audio/mp3");
        defaultMappings.put("wav",  "audio/wav");
        defaultMappings.put("aac",  "audio/aac");
        defaultMappings.put("flac", "audio/flac");
        defaultMappings.put("mpa",  "audio/m4a");
        defaultMappings.put("mpga", "audio/mpga");
        defaultMappings.put("opus", "audio/opus");
        defaultMappings.put("pcm",  "audio/pcm");

        // Mime types from video requirements for Vertex AI Gemini
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/video-understanding
        defaultMappings.put("mp4",    "video/mp4");
        defaultMappings.put("mpeg",   "video/mpeg");
        defaultMappings.put("mpg",    "video/mpg");
        defaultMappings.put("mpegps", "video/mpegps");
        defaultMappings.put("mov",    "video/mov");
        defaultMappings.put("avi",    "video/avi");
        defaultMappings.put("flv",    "video/x-flv");
        defaultMappings.put("webm",   "video/webm");
        defaultMappings.put("mmv",    "video/wmv");
        defaultMappings.put("3gpp",   "video/3gpp");
    }

    private final Map<String, String> mappings;

    /**
     * Create a file mime-type detector, using the internal default mappings,
     * and fallback to JDK's built-in capabilities, when no suitable mapping is found.
     */
    public CustomMimeTypesFileTypeDetector() {
        this(defaultMappings);
    }

    /**
     * Create a file mime-type detector, using your own custom file extension
     * to mime-type mappings, with a fallback to JDK's built-in capabilities,
     * when no suitable mapping is found
     *
     * @param customMapping map of custom file extension to mime-type mapping
     */
    public CustomMimeTypesFileTypeDetector(Map<String, String> customMapping) {
        this.mappings = Collections.unmodifiableMap(customMapping);
    }

    /**
     * Guess the mime-type of a given path.
     *
     * @param path the path to the file whose mime-type we want to retrieve.
     *
     * @return a string representing the mime-type of the file denoted by the path parameter,
     *     returns null if no mapping was found.
     */
    @Override
    public String probeContentType(Path path) {
        String extension = extension(path).toLowerCase();

        if (mappings.containsKey(extension)) {
            return mappings.get(extension);
        }

        try {
            return Files.probeContentType(path);
        } catch (IOException e) {
            return null;
        }
    }

    public String probeContentType(String path) {
        return probeContentType(Path.of(path));
    }

    /**
     * Guess the mime-type of a given URI.
     *
     * @param uri the URI to the file whose mime-type we want to retrieve.
     *
     * @return a string representing the mime-type of the file denoted by the URI parameter,
     *     returns null if no mapping was found.
     */
    public String probeContentType(URI uri) {
        // First let's try to guess via the Path
        Path path = Path.of(uri.getPath());
        String mimeTypeFromPath = probeContentType(path);
        if (mimeTypeFromPath != null) {
            return mimeTypeFromPath;
        }

        // Second, let's see if URLConnection can guess from the file name
        String mimeTypeGuessedFromUrlCon = URLConnection.guessContentTypeFromName(path.getFileName().toString());
        if (mimeTypeGuessedFromUrlCon != null) {
            return mimeTypeGuessedFromUrlCon;
        }

        // Third, the most costly network-hop approach to check
        // the content-type returned when opening a stream
        // Inspired from langchain4j-dashscope module: in EnhancedFileTypeDetector
        try (InputStream in = new BufferedInputStream(
            Files.newInputStream(path, StandardOpenOption.READ))) {
            return URLConnection.guessContentTypeFromStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    static String extension(Path path) {
        String fileName = path.getFileName().toFile().toString();
        int lastDotPosition = fileName.lastIndexOf('.');
        return lastDotPosition > 0 ? fileName.substring(lastDotPosition + 1) : "";
    }
}
