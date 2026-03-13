package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GoogleGenAiContentMapper {

    private static final Logger log = LoggerFactory.getLogger(GoogleGenAiContentMapper.class);

    static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().registerModule(new Jdk8Module()).disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = new HashMap<>();

    static {
        // image requirements
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

        // audio requirements
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/audio-understanding
        EXTENSION_TO_MIME_TYPE.put("mp3", "audio/mp3");
        EXTENSION_TO_MIME_TYPE.put("wav", "audio/wav");
        EXTENSION_TO_MIME_TYPE.put("aac", "audio/aac");
        EXTENSION_TO_MIME_TYPE.put("flac", "audio/flac");
        EXTENSION_TO_MIME_TYPE.put("m4a", "audio/m4a");
        EXTENSION_TO_MIME_TYPE.put("mpga", "audio/mpga");
        EXTENSION_TO_MIME_TYPE.put("opus", "audio/opus");
        EXTENSION_TO_MIME_TYPE.put("pcm", "audio/pcm");

        // video requirements
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

        // document understanding
        // https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/document-understanding
        EXTENSION_TO_MIME_TYPE.put("pdf", "application/pdf");
        EXTENSION_TO_MIME_TYPE.put("txt", "text/plain");
        EXTENSION_TO_MIME_TYPE.put("log", "text/plain");
        EXTENSION_TO_MIME_TYPE.put("csv", "text/plain");
        EXTENSION_TO_MIME_TYPE.put("tsv", "text/plain");
        EXTENSION_TO_MIME_TYPE.put("xml", "text/plain");
        EXTENSION_TO_MIME_TYPE.put("json", "text/plain");
    }

    private static final String SYSTEM_ROLE = "system";
    private static final String USER_ROLE = "user";
    private static final String MODEL_ROLE = "model";
    private static final String FUNCTION_ROLE = "function";

    static Content toSystemInstruction(List<ChatMessage> messages) {
        String systemInstructions = messages.stream()
                .filter(m -> m instanceof SystemMessage)
                .map(m -> ((SystemMessage) m).text())
                .collect(Collectors.joining("\n"));

        if (systemInstructions.isEmpty()) {
            return null;
        }

        return Content.builder()
                .role(SYSTEM_ROLE)
                .parts(Part.builder().text(systemInstructions).build())
                .build();
    }

    static List<Content> toContents(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> !(m instanceof SystemMessage))
                .map(GoogleGenAiContentMapper::toContent)
                .collect(Collectors.toList());
    }

    static Content toContent(ChatMessage message) {
        if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return Content.builder().role(USER_ROLE).parts(toParts(userMessage)).build();

        } else if (message instanceof AiMessage) {
            AiMessage aiMsg = (AiMessage) message;
            List<Part> parts = new ArrayList<>();
            if (aiMsg.text() != null) {
                parts.add(Part.builder().text(aiMsg.text()).build());
            }
            if (aiMsg.toolExecutionRequests() != null) {
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    Map<String, Object> args = new HashMap<>();
                    if (req.arguments() != null && !req.arguments().isEmpty()) {
                        try {
                            args = OBJECT_MAPPER.readValue(req.arguments(), new TypeReference<>() {});
                        } catch (Exception e) {
                            log.warn(
                                    "Failed to parse tool execution request arguments {}: {}",
                                    req.arguments(),
                                    e.getMessage());
                        }
                    }
                    parts.add(Part.builder()
                            .functionCall(FunctionCall.builder()
                                    .name(req.name())
                                    .args(args)
                                    .build())
                            .build());
                }
            }
            return Content.builder().role(MODEL_ROLE).parts(parts).build();

        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolMsg = (ToolExecutionResultMessage) message;
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("result", toolMsg.text());

            return Content.builder()
                    .role(FUNCTION_ROLE)
                    .parts(Part.builder()
                            .functionResponse(FunctionResponse.builder()
                                    .name(toolMsg.toolName())
                                    .response(responseMap)
                                    .build())
                            .build())
                    .build();
        }
        throw new IllegalArgumentException("Unknown message type: " + message.type());
    }

    static ChatResponse toChatResponse(GenerateContentResponse response) {
        List<Candidate> candidates = response.candidates().orElse(Collections.emptyList());

        if (candidates.isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Empty response"))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.OTHER)
                    .build();
        }

        Candidate candidate = candidates.get(0);
        Content content = candidate.content().orElse(null);

        StringBuilder textBuilder = new StringBuilder();
        List<ToolExecutionRequest> toolRequests = new ArrayList<>();

        if (content != null) {
            List<Part> parts = content.parts().orElse(Collections.emptyList());
            for (Part part : parts) {
                if (part.text().isPresent()) textBuilder.append(part.text().get());

                if (part.functionCall().isPresent()) {
                    FunctionCall fc = part.functionCall().get();
                    String fnName = fc.name().orElse("unknown");
                    String jsonArgs;
                    try {
                        jsonArgs = OBJECT_MAPPER.writeValueAsString(fc.args());
                    } catch (Exception e) {
                        jsonArgs = "{}";
                        log.error("", e);
                    }

                    toolRequests.add(ToolExecutionRequest.builder()
                            .name(fnName)
                            .arguments(jsonArgs)
                            .build());
                }
            }
        }

        String text = textBuilder.toString();
        AiMessage aiMessage;
        if (!toolRequests.isEmpty() && !isNullOrEmpty(text)) {
            aiMessage = new AiMessage(text, toolRequests);
        } else if (!toolRequests.isEmpty()) {
            aiMessage = AiMessage.from(toolRequests);
        } else {
            aiMessage = AiMessage.from(text);
        }

        TokenUsage usage = response.usageMetadata()
                .map(meta -> new TokenUsage(
                        meta.promptTokenCount().isPresent()
                                ? meta.promptTokenCount().get()
                                : 0,
                        meta.candidatesTokenCount().isPresent()
                                ? meta.candidatesTokenCount().get()
                                : 0))
                .orElse(new TokenUsage(0, 0));

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .tokenUsage(usage)
                .finishReason(FinishReason.STOP)
                .build();
    }

    private static List<Part> toParts(UserMessage userMessage) {
        return userMessage.contents().stream()
                .map(GoogleGenAiContentMapper::map)
                .collect(Collectors.toList());
    }

    private static Part map(dev.langchain4j.data.message.Content content) {
        if (content instanceof TextContent textContent) {
            return Part.builder().text(textContent.text()).build();
        } else if (content instanceof ImageContent imageContent) {
            Image image = imageContent.image();
            return getPart(image.url(), image.mimeType(), image.base64Data(), null);
        } else if (content instanceof AudioContent audioContent) {
            Audio audio = audioContent.audio();
            return getPart(audio.url(), audio.mimeType(), audio.base64Data(), audio.binaryData());
        } else if (content instanceof VideoContent videoContent) {
            Video video = videoContent.video();
            return getPart(video.url(), video.mimeType(), video.base64Data(), null);
        } else if (content instanceof PdfFileContent pdfFileContent) {
            PdfFile pdfFile = pdfFileContent.pdfFile();
            return getPart(pdfFile.url(), pdfFile.mimeType(), pdfFile.base64Data(), null);
        } else {
            throw illegalArgument("Unknown content type: " + content);
        }
    }

    private static Part getPart(URI url, String mimeType, String base64data, byte[] binaryData) {
        if (url != null) {
            String effectiveMimeType = getOrDefault(mimeType, () -> detectMimeType(url));
            if (url.getScheme().equals("gs")) {
                return fromMimeTypeAndData(effectiveMimeType, url);
            } else {
                return fromMimeTypeAndData(effectiveMimeType, readBytes(url));
            }
        } else if (binaryData != null) {
            return fromMimeTypeAndData(mimeType, binaryData);
        } else {
            return fromMimeTypeAndData(mimeType, Base64.getDecoder().decode(base64data));
        }
    }

    static String detectMimeType(URI url) {
        String[] pathParts = url.getPath().split("\\.");
        if (pathParts.length > 1) {
            String extension = pathParts[pathParts.length - 1].toLowerCase();
            String mimeType = EXTENSION_TO_MIME_TYPE.get(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
        throw illegalArgument("Unable to detect the MIME type of '%s'. Please provide it explicitly.", url);
    }

    static Part fromMimeTypeAndData(String mimeType, byte[] bytes) {
        return Part.fromBytes(bytes, mimeType);
    }

    static Part fromMimeTypeAndData(String mimeType, URI uri) {
        return Part.fromUri(uri.toString(), mimeType);
    }

    private static byte[] readBytes(URI uri) {
        try (InputStream inputStream = Files.newInputStream(Paths.get(uri))) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read data from " + uri, e);
        }
    }

    private GoogleGenAiContentMapper() {}
}
