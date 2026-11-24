package dev.langchain4j.model.vertexai.gemini;

import static com.google.cloud.vertexai.generativeai.PartMaker.fromMimeTypeAndData;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Collections.singletonList;

import com.google.cloud.vertexai.api.FunctionResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

class PartsMapper {

    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = new HashMap<>();

    static {
        // see image requirements
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/image-understanding#image-requirements
        EXTENSION_TO_MIME_TYPE.put("avif", "image/avif");
        EXTENSION_TO_MIME_TYPE.put("bmp", "image/bmp");
        EXTENSION_TO_MIME_TYPE.put("gif", "image/gif");
        EXTENSION_TO_MIME_TYPE.put("jpe", "image/jpeg");
        EXTENSION_TO_MIME_TYPE.put("jpeg", "image/jpeg");
        EXTENSION_TO_MIME_TYPE.put("jpg", "image/jpeg");
        EXTENSION_TO_MIME_TYPE.put("png", "image/png");
        EXTENSION_TO_MIME_TYPE.put("svg", "image/svg+xml");
        EXTENSION_TO_MIME_TYPE.put("tif", "image/tiff");
        EXTENSION_TO_MIME_TYPE.put("tiff", "image/tiff");
        EXTENSION_TO_MIME_TYPE.put("webp", "image/webp");

        // see audio requirements
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/audio-understanding
        EXTENSION_TO_MIME_TYPE.put("mp3", "audio/mp3");
        EXTENSION_TO_MIME_TYPE.put("wav", "audio/wav");
        EXTENSION_TO_MIME_TYPE.put("aac", "audio/aac");
        EXTENSION_TO_MIME_TYPE.put("flac", "audio/flac");
        EXTENSION_TO_MIME_TYPE.put("m4a", "audio/m4a");
        EXTENSION_TO_MIME_TYPE.put("mpga", "audio/mpga");
        EXTENSION_TO_MIME_TYPE.put("opus", "audio/opus");
        EXTENSION_TO_MIME_TYPE.put("pcm", "audio/pcm");

        // see video requirements:
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/video-understanding
        EXTENSION_TO_MIME_TYPE.put("mp4", "video/mp4");
        EXTENSION_TO_MIME_TYPE.put("mpeg", "video/mpeg");
        EXTENSION_TO_MIME_TYPE.put("mpg", "video/mpg");
        EXTENSION_TO_MIME_TYPE.put("mpegps", "video/mpegps");
        EXTENSION_TO_MIME_TYPE.put("mov", "video/mov");
        EXTENSION_TO_MIME_TYPE.put("avi", "video/avi");
        EXTENSION_TO_MIME_TYPE.put("flv", "video/x-flv");
        EXTENSION_TO_MIME_TYPE.put("webm", "video/webm");
        EXTENSION_TO_MIME_TYPE.put("wmv", "video/wmv");
        EXTENSION_TO_MIME_TYPE.put("3gpp", "video/3gpp");

        // see document understanding:
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/document-understanding
        EXTENSION_TO_MIME_TYPE.put("pdf", "application/pdf");
    }

    static List<Part> map(ChatMessage message, MediaFetcher mediaFetcher) {
        if (message instanceof AiMessage aiMessage) {

            List<Part> parts = new ArrayList<>();

            if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                parts.add(Part.newBuilder().setText(aiMessage.text()).build());
            }

            if (aiMessage.hasToolExecutionRequests()) {
                List<Part> fnCallReqParts = aiMessage.toolExecutionRequests().stream()
                        .map(FunctionCallHelper::fromToolExecutionRequest)
                        .map(fnCall -> Part.newBuilder().setFunctionCall(fnCall).build())
                        .toList();

                parts.addAll(fnCallReqParts);
            }

            return parts;
        } else if (message instanceof UserMessage userMessage) {
            return userMessage.contents().stream()
                    .map(content -> map(content, mediaFetcher))
                    .toList();
        } else if (message instanceof SystemMessage systemMessage) {
            return singletonList(
                    Part.newBuilder().setText((systemMessage).text()).build());
        } else if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            if (!toolExecutionResultMessage.hasSingleText()) {
                throw new UnsupportedFeatureException(
                        "Vertex AI Gemini does not support non-text content in tool results. "
                                + "Only text content is supported.");
            }
            String functionResponseText = toolExecutionResultMessage.text();
            Struct responseStruct = parseFunctionResponseToStruct(functionResponseText);

            return singletonList(Part.newBuilder()
                    .setFunctionResponse(FunctionResponse.newBuilder()
                            .setName(toolExecutionResultMessage.toolName())
                            .setResponse(responseStruct)
                            .build())
                    .build());
        } else {
            throw illegalArgument(message.type() + " message is not supported by Gemini");
        }
    }

    private static Part map(Content content, MediaFetcher mediaFetcher) {
        if (content instanceof TextContent text) {
            return map(text);
        } else if (content instanceof ImageContent image) {
            return map(image, mediaFetcher);
        } else if (content instanceof AudioContent audio) {
            return map(audio, mediaFetcher);
        } else if (content instanceof VideoContent video) {
            return map(video, mediaFetcher);
        } else if (content instanceof PdfFileContent pdf) {
            return map(pdf, mediaFetcher);
        } else {
            throw illegalArgument("Unknown content type: " + content);
        }
    }

    private static Part map(TextContent content) {
        return Part.newBuilder().setText(content.text()).build();
    }

    static Part map(ImageContent content, MediaFetcher mediaFetcher) {
        Image image = content.image();
        return getPart(image.url(), image.mimeType(), image.base64Data(), MediaKind.IMAGE, mediaFetcher);
    }

    static Part map(AudioContent content, MediaFetcher mediaFetcher) {
        Audio audio = content.audio();
        return getPart(audio.url(), audio.mimeType(), audio.base64Data(), MediaKind.AUDIO, mediaFetcher);
    }

    static Part map(VideoContent content, MediaFetcher mediaFetcher) {
        Video video = content.video();
        return getPart(video.url(), video.mimeType(), video.base64Data(), MediaKind.VIDEO, mediaFetcher);
    }

    static Part map(PdfFileContent content, MediaFetcher mediaFetcher) {
        PdfFile pdfFile = content.pdfFile();
        return getPart(pdfFile.url(), pdfFile.mimeType(), pdfFile.base64Data(), MediaKind.PDF, mediaFetcher);
    }

    private static Part getPart(
            @Nullable URI url,
            @Nullable String mimeType,
            String base64data,
            MediaKind mediaKind,
            MediaFetcher mediaFetcher) {
        if (url != null) {
            if (isPassedOn(url)) {
                return fromMimeTypeAndData(getOrDefault(mimeType, () -> detectMimeType(url)), url);
            }
            FetchedMedia media = fetch(mediaFetcher, url);
            return fromMimeTypeAndData(mimeType(mimeType, url, media, mediaKind), media.bytes());
        }
        return fromMimeTypeAndData(mimeType, Base64.getDecoder().decode(base64data));
    }

    private static FetchedMedia fetch(MediaFetcher mediaFetcher, URI url) {
        return mediaFetcher.fetch(MediaFetchRequest.from(url));
    }

    /**
     * Returns the URLs that {@link #map(ChatMessage, MediaFetcher)} downloads with its {@link MediaFetcher}: the URLs
     * of all media in user messages, except Cloud Storage URLs, which are passed on to Gemini.
     */
    static List<URI> mediaUrls(List<ChatMessage> messages) {
        List<URI> urls = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                for (Content content : userMessage.contents()) {
                    URI url = mediaUrl(content);
                    if (url != null && !isPassedOn(url)) {
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }

    private static @Nullable URI mediaUrl(Content content) {
        if (content instanceof ImageContent image) {
            return image.image().url();
        } else if (content instanceof AudioContent audio) {
            return audio.audio().url();
        } else if (content instanceof VideoContent video) {
            return video.video().url();
        } else if (content instanceof PdfFileContent pdf) {
            return pdf.pdfFile().url();
        }
        return null;
    }

    private static boolean isPassedOn(URI url) {
        return "gs".equals(url.getScheme());
    }

    /**
     * Returns the MIME type of downloaded media: the given one, otherwise the one of the extension of the URL,
     * otherwise the one the server reported, but only if it fits the kind of the content (for example, a web page
     * given as a video is not sent as a video).
     */
    private static String mimeType(@Nullable String mimeType, URI url, FetchedMedia media, MediaKind mediaKind) {
        if (mimeType != null) {
            return mimeType;
        }
        String mimeTypeFromExtension = mimeTypeFromExtension(url);
        if (mimeTypeFromExtension != null) {
            return mimeTypeFromExtension;
        }
        String reportedMimeType = media.mimeType();
        if (reportedMimeType != null && mediaKind.matches(reportedMimeType)) {
            return reportedMimeType;
        }
        throw unknownMimeType(url, reportedMimeType);
    }

    static String detectMimeType(URI url) {
        String mimeType = mimeTypeFromExtension(url);
        if (mimeType == null) {
            throw unknownMimeType(url, null);
        }
        return mimeType;
    }

    private static @Nullable String mimeTypeFromExtension(URI url) {
        if (url.getPath() == null) {
            return null;
        }
        String[] pathParts = url.getPath().split("\\.");
        if (pathParts.length > 1) {
            String extension = pathParts[pathParts.length - 1].toLowerCase(Locale.ROOT);
            return EXTENSION_TO_MIME_TYPE.get(extension);
        }
        return null;
    }

    private static IllegalArgumentException unknownMimeType(URI url, @Nullable String reportedMimeType) {
        return illegalArgument(
                "Unable to detect the MIME type of '%s'%s. Please provide it explicitly.",
                withoutPathAndQuery(url),
                reportedMimeType == null ? "" : " (the server answered with " + reportedMimeType + ")");
    }

    /**
     * Shows the scheme, the host and the port of the URL only, because the path and the query can contain
     * credentials, for example in a presigned URL.
     */
    private static String withoutPathAndQuery(URI url) {
        String scheme = url.getScheme() == null ? "" : url.getScheme() + ":";
        if (url.getHost() == null) {
            return scheme + "...";
        }
        String port = url.getPort() == -1 ? "" : ":" + url.getPort();
        return scheme + "//" + url.getHost() + port + "/...";
    }

    private enum MediaKind {
        IMAGE,
        AUDIO,
        VIDEO,
        PDF;

        boolean matches(String mimeType) {
            String lowerCaseMimeType = mimeType.toLowerCase(Locale.ROOT);
            return switch (this) {
                case IMAGE -> lowerCaseMimeType.startsWith("image/");
                case AUDIO -> lowerCaseMimeType.startsWith("audio/");
                case VIDEO -> lowerCaseMimeType.startsWith("video/");
                case PDF -> lowerCaseMimeType.equals("application/pdf");
            };
        }
    }

    private static Struct parseFunctionResponseToStruct(String functionResponseText) {
        try {
            Struct.Builder structBuilder = Struct.newBuilder();
            JsonFormat.parser().merge(functionResponseText, structBuilder);
            return structBuilder.build();
        } catch (InvalidProtocolBufferException e) {
            // Not a JSON object
        }

        try {
            String wrappedJson = "{\"result\":" + functionResponseText + "}";
            Struct.Builder structBuilder = Struct.newBuilder();
            JsonFormat.parser().merge(wrappedJson, structBuilder);
            return structBuilder.build();
        } catch (InvalidProtocolBufferException e) {
            // Not a valid JSON
        }

        return Struct.newBuilder()
                .putFields(
                        "result",
                        Value.newBuilder().setStringValue(functionResponseText).build())
                .build();
    }
}
