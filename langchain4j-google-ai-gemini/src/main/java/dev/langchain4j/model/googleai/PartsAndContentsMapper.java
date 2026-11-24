package dev.langchain4j.model.googleai;

import static dev.langchain4j.data.message.AiMessage.GENERATED_IMAGES_KEY;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.googleai.FunctionMapper.toToolExecutionRequests;
import static dev.langchain4j.model.googleai.Json.fromJson;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.CustomMimeTypesFileTypeDetector;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiBlob;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiCodeExecutionResult;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiCodeExecutionResult.GeminiOutcome;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiExecutableCode;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiFileData;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiFunctionCall;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiFunctionResponse;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

final class PartsAndContentsMapper {

    private PartsAndContentsMapper() {}

    static final String THINKING_SIGNATURE_KEY =
            "thinking_signature"; // do not change, will break backward compatibility!

    private static final CustomMimeTypesFileTypeDetector mimeTypeDetector = new CustomMimeTypesFileTypeDetector();

    // Pattern to parse data URIs: data:[<mediatype>][;base64],<data>
    private static final Pattern DATA_URI_PATTERN = Pattern.compile("^data:([^;,]+)(?:;[^,]*)?,(.*)$");

    static GeminiContent.GeminiPart fromContentToGPart(Content content, MediaFetcher mediaFetcher) {
        return fromContentToGPart(content, false, mediaFetcher);
    }

    static GeminiContent.GeminiPart fromContentToGPart(
            Content content, boolean mediaResolutionPerPartEnabled, MediaFetcher mediaFetcher) {
        if (content instanceof TextContent textContent) {
            return GeminiContent.GeminiPart.builder().text(textContent.text()).build();
        } else if (content instanceof ImageContent imageContent) {
            Image image = imageContent.image();
            GeminiMediaResolution mediaResolution =
                    mediaResolutionPerPartEnabled ? toGeminiMediaResolution(imageContent.detailLevel()) : null;

            if (!isNullOrBlank(image.base64Data())) {
                return GeminiContent.GeminiPart.builder()
                        .inlineData(new GeminiBlob(image.mimeType(), image.base64Data()))
                        .mediaResolution(mediaResolution)
                        .build();
            } else if (image.url() != null) {
                URI url = image.url();
                // Handle data URIs (e.g., "data:image/png;base64,iVBORw0KG...")
                if (url.getScheme() != null && url.getScheme().equals("data")) {
                    return GeminiContent.GeminiPart.builder()
                            .inlineData(parseDataUri(url))
                            .mediaResolution(mediaResolution)
                            .build();
                } else if (isDownloaded(url)) {
                    FetchedMedia media = fetch(mediaFetcher, url);
                    String base64Data = Base64.getEncoder().encodeToString(media.bytes());
                    return GeminiContent.GeminiPart.builder()
                            .inlineData(new GeminiBlob(mimeType(image.mimeType(), url, media), base64Data))
                            .mediaResolution(mediaResolution)
                            .build();
                } else {
                    return GeminiContent.GeminiPart.builder()
                            .fileData(new GeminiFileData(
                                    getOrDefault(image.mimeType(), mimeTypeDetector.probeContentTypeFromName(url)),
                                    url.toString()))
                            .mediaResolution(mediaResolution)
                            .build();
                }
            } else {
                throw new IllegalArgumentException("Image should contain either base64 data or url");
            }
        } else if (content instanceof AudioContent audioContent) {
            URI uri = audioContent.audio().url();
            if (uri != null) {
                // Handle data URIs for audio
                if (uri.getScheme() != null && uri.getScheme().equals("data")) {
                    return GeminiContent.GeminiPart.builder()
                            .inlineData(parseDataUri(uri))
                            .build();
                }
                return GeminiContent.GeminiPart.builder()
                        .fileData(new GeminiFileData(mimeTypeDetector.probeContentTypeFromName(uri), uri.toString()))
                        .build();
            } else {
                return GeminiContent.GeminiPart.builder()
                        .inlineData(new GeminiBlob(
                                audioContent.audio().mimeType(),
                                audioContent.audio().base64Data()))
                        .build();
            }
        } else if (content instanceof VideoContent videoContent) {
            URI uri = videoContent.video().url();
            if (uri != null) {
                // Handle data URIs for video
                if (uri.getScheme() != null && uri.getScheme().equals("data")) {
                    return GeminiContent.GeminiPart.builder()
                            .inlineData(parseDataUri(uri))
                            .build();
                }
                return GeminiContent.GeminiPart.builder()
                        .fileData(new GeminiFileData(mimeTypeDetector.probeContentTypeFromName(uri), uri.toString()))
                        .build();
            } else {
                return GeminiContent.GeminiPart.builder()
                        .inlineData(new GeminiBlob(
                                videoContent.video().mimeType(),
                                videoContent.video().base64Data()))
                        .build();
            }
        } else if (content instanceof PdfFileContent pdfFileContent) {
            PdfFile pdfFile = pdfFileContent.pdfFile();

            URI uri = pdfFile.url();
            if (uri != null) {
                // Handle data URIs for PDF
                if (uri.getScheme() != null && uri.getScheme().equals("data")) {
                    return GeminiContent.GeminiPart.builder()
                            .inlineData(parseDataUri(uri))
                            .build();
                }
                return GeminiContent.GeminiPart.builder()
                        .fileData(new GeminiFileData(mimeTypeDetector.probeContentTypeFromName(uri), uri.toString()))
                        .build();
            } else {
                return GeminiContent.GeminiPart.builder()
                        .inlineData(new GeminiBlob(pdfFile.mimeType(), pdfFile.base64Data()))
                        .build();
            }
        } else {
            throw new UnsupportedFeatureException("Unsupported content type: " + content.type());
        }
    }

    private static FetchedMedia fetch(MediaFetcher mediaFetcher, URI url) {
        return mediaFetcher.fetch(MediaFetchRequest.from(url));
    }

    /**
     * Returns the URLs that {@link #fromMessageToGContent} downloads with its {@link MediaFetcher}: the URLs of images
     * that are sent to Gemini as content.
     */
    static List<URI> mediaUrls(List<ChatMessage> messages) {
        List<URI> urls = new ArrayList<>();
        for (ChatMessage message : messages) {
            List<Content> contents;
            if (message instanceof UserMessage userMessage) {
                contents = userMessage.contents();
            } else if (message instanceof ToolExecutionResultMessage toolResultMessage) {
                contents = toolResultMessage.contents();
            } else {
                continue;
            }
            for (Content content : contents) {
                if (content instanceof ImageContent imageContent) {
                    Image image = imageContent.image();
                    if (isNullOrBlank(image.base64Data()) && image.url() != null && isDownloaded(image.url())) {
                        urls.add(image.url());
                    }
                }
            }
        }
        return urls;
    }

    private static boolean isDownloaded(URI url) {
        return url.getScheme() != null && url.getScheme().startsWith("http");
    }

    /**
     * Returns the MIME type of a downloaded image: the given one, otherwise the one of the file name in the URL,
     * otherwise the one the server reported, but only if it is an image type (a web page is not sent as an image).
     */
    private static @Nullable String mimeType(@Nullable String mimeType, URI url, FetchedMedia media) {
        if (mimeType != null) {
            return mimeType;
        }
        String mimeTypeFromName = mimeTypeDetector.probeContentTypeFromName(url);
        if (mimeTypeFromName != null) {
            return mimeTypeFromName;
        }
        String reportedMimeType = media.mimeType();
        if (reportedMimeType == null || reportedMimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return reportedMimeType;
        }
        throw illegalArgument(
                "Unable to detect the MIME type of '%s' (the server answered with %s). Please provide it explicitly.",
                withoutPathAndQuery(url),
                reportedMimeType);
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

    /**
     * Maps DetailLevel from ImageContent to GeminiMediaResolution.
     *
     * @param detailLevel the detail level from ImageContent
     * @return the corresponding GeminiMediaResolution, or null if detailLevel is null
     */
    private static GeminiMediaResolution toGeminiMediaResolution(ImageContent.DetailLevel detailLevel) {
        if (detailLevel == null) {
            return null;
        }
        return switch (detailLevel) {
            case LOW -> GeminiMediaResolution.of(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_LOW);
            case MEDIUM -> GeminiMediaResolution.of(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_MEDIUM);
            case HIGH -> GeminiMediaResolution.of(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_HIGH);
            case ULTRA_HIGH -> GeminiMediaResolution.of(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_ULTRA_HIGH);
            case AUTO -> GeminiMediaResolution.of(GeminiMediaResolutionLevel.MEDIA_RESOLUTION_UNSPECIFIED);
        };
    }

    static AiMessage fromGPartsToAiMessage(
            List<GeminiContent.GeminiPart> parts, boolean includeCodeExecutionOutput, Boolean returnThinking) {

        List<GeminiContent.GeminiPart> safeParts = Objects.requireNonNullElse(parts, List.of());
        StringBuilder fullText = new StringBuilder();
        List<String> thoughts = new ArrayList<>();
        List<String> thoughtSignatures = new ArrayList<>();
        List<GeminiFunctionCall> functionCalls = new ArrayList<>();
        List<Image> generatedImages = new ArrayList<>();

        for (GeminiContent.GeminiPart part : safeParts) {
            GeminiExecutableCode executableCode = part.executableCode();
            if (executableCode != null && includeCodeExecutionOutput) {
                fullText.append("Code executed:\n")
                        .append("```python\n")
                        .append(executableCode.code())
                        .append("\n```\n");
            }

            GeminiCodeExecutionResult codeExecutionResult = part.codeExecutionResult();
            if (codeExecutionResult != null && includeCodeExecutionOutput) {
                GeminiOutcome outcome = codeExecutionResult.outcome();

                if (outcome != GeminiOutcome.OUTCOME_OK) {
                    fullText.append("Code execution failed: **")
                            .append(outcome.name())
                            .append("**\n")
                            .append(part.text() != null ? part.text() : "");
                } else {
                    fullText.append("Output:\n")
                            .append("```\n")
                            .append(codeExecutionResult.output())
                            .append("```\n");
                }
            }

            String text = part.text();
            if (isNotNullOrEmpty(text)) {
                if (Boolean.TRUE.equals(part.isThought())) {
                    if (Boolean.TRUE.equals(returnThinking)) {
                        thoughts.add(text);
                    } else if (returnThinking == null) { // for backward compatibility
                        if (!fullText.isEmpty()) {
                            fullText.append("\n\n");
                        }
                        fullText.append(text);
                    }
                } else {
                    if (!fullText.isEmpty()) {
                        fullText.append("\n\n");
                    }
                    fullText.append(text);
                }
            }

            String thoughtSignature = part.thoughtSignature();
            if (Boolean.TRUE.equals(returnThinking) && isNotNullOrEmpty(thoughtSignature)) {
                thoughtSignatures.add(thoughtSignature);
            }

            if (part.functionCall() != null) {
                functionCalls.add(part.functionCall());
            }

            GeminiBlob inlineData = part.inlineData();
            if (inlineData != null
                    && inlineData.mimeType() != null
                    && inlineData.mimeType().startsWith("image/")
                    && inlineData.data() != null) {
                Image generatedImage = Image.builder()
                        .base64Data(inlineData.data())
                        .mimeType(inlineData.mimeType())
                        .build();
                generatedImages.add(generatedImage);
            }
        }

        String text = fullText.toString();
        String thinking = thoughts.stream().collect(joining("\n\n"));
        String thinkingSignature = thoughtSignatures.stream().collect(joining("\n\n"));

        Map<String, Object> attributes = new java.util.HashMap<>();
        if (isNotNullOrEmpty(thinkingSignature)) {
            attributes.put(THINKING_SIGNATURE_KEY, thinkingSignature);
        }
        if (!generatedImages.isEmpty()) {
            attributes.put(GENERATED_IMAGES_KEY, generatedImages);
        }

        return AiMessage.builder()
                .text(isNullOrEmpty(text) ? null : text)
                .thinking(isNullOrEmpty(thinking) ? null : thinking)
                .toolExecutionRequests(toToolExecutionRequests(functionCalls))
                .attributes(attributes.isEmpty() ? Map.of() : attributes)
                .build();
    }

    static List<GeminiContent> fromMessageToGContent(
            List<ChatMessage> messages,
            GeminiContent systemInstruction,
            boolean sendThinking,
            MediaFetcher mediaFetcher) {
        return fromMessageToGContent(messages, systemInstruction, sendThinking, false, mediaFetcher);
    }

    static List<GeminiContent> fromMessageToGContent(
            List<ChatMessage> messages,
            GeminiContent systemInstruction,
            boolean sendThinking,
            boolean mediaResolutionPerPartEnabled,
            MediaFetcher mediaFetcher) {
        return messages.stream()
                .map(msg -> {
                    switch (msg.type()) {
                        case SYSTEM:
                            SystemMessage systemMessage = (SystemMessage) msg;

                            if (systemInstruction != null) {
                                systemInstruction.addPart(GeminiContent.GeminiPart.builder()
                                        .text(systemMessage.text())
                                        .build());
                                return null;
                            }

                            if (isNotNullOrEmpty(systemMessage.text())) {
                                return new GeminiContent(
                                        List.of(GeminiContent.GeminiPart.builder()
                                                .text(systemMessage.text())
                                                .build()),
                                        GeminiRole.MODEL.toString());
                            }

                            return null;
                        case AI:
                            AiMessage aiMessage = (AiMessage) msg;

                            List<GeminiContent.GeminiPart> parts = new ArrayList<>();

                            if (sendThinking && isNotNullOrEmpty(aiMessage.thinking())) {
                                parts.add(GeminiContent.GeminiPart.builder()
                                        .text(aiMessage.thinking())
                                        .thought(true)
                                        .build());
                            }

                            if (isNotNullOrEmpty(aiMessage.text())) {
                                parts.add(GeminiContent.GeminiPart.builder()
                                        .text(aiMessage.text())
                                        .build());
                            }

                            if (aiMessage.hasToolExecutionRequests()) {
                                String thoughtSignature = null;
                                if (sendThinking) {
                                    thoughtSignature = aiMessage.attribute(THINKING_SIGNATURE_KEY, String.class);
                                }
                                parts.addAll(toGeminiParts(aiMessage.toolExecutionRequests(), thoughtSignature));
                            }

                            return new GeminiContent(parts, GeminiRole.MODEL.toString());

                        case USER:
                            UserMessage userMessage = (UserMessage) msg;

                            return new GeminiContent(
                                    userMessage.contents().stream()
                                            .map(content -> fromContentToGPart(
                                                    content, mediaResolutionPerPartEnabled, mediaFetcher))
                                            .collect(Collectors.toList()),
                                    GeminiRole.USER.toString());
                        case TOOL_EXECUTION_RESULT:
                            ToolExecutionResultMessage toolResultMessage = (ToolExecutionResultMessage) msg;

                            if (!toolResultMessage.hasSingleText()) {
                                List<GeminiContent.GeminiPart> toolParts = new ArrayList<>();
                                Map<String, String> responseMap = new HashMap<>();
                                for (dev.langchain4j.data.message.Content content : toolResultMessage.contents()) {
                                    if (content instanceof TextContent textContent) {
                                        responseMap.put("response", textContent.text());
                                    } else if (content instanceof ImageContent imageContent) {
                                        toolParts.add(fromContentToGPart(
                                                imageContent, mediaResolutionPerPartEnabled, mediaFetcher));
                                    } else {
                                        throw new UnsupportedFeatureException(
                                                "Google AI Gemini does not support content type '" + content.type()
                                                        + "' in tool results.");
                                    }
                                }
                                if (responseMap.isEmpty()) {
                                    responseMap.put("response", "");
                                }
                                toolParts.add(
                                        0,
                                        GeminiContent.GeminiPart.builder()
                                                .functionResponse(new GeminiFunctionResponse(
                                                        toolResultMessage.id(),
                                                        toolResultMessage.toolName(),
                                                        responseMap))
                                                .build());
                                return new GeminiContent(toolParts, GeminiRole.USER.toString());
                            }

                            return new GeminiContent(
                                    List.of(GeminiContent.GeminiPart.builder()
                                            .functionResponse(new GeminiFunctionResponse(
                                                    toolResultMessage.id(),
                                                    toolResultMessage.toolName(),
                                                    Map.of("response", toolResultMessage.text())))
                                            .build()),
                                    GeminiRole.USER.toString());
                        default:
                            return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Parses a data URI and returns a GeminiBlob with the extracted MIME type and base64 data.
     *
     * @param uri the data URI to parse (e.g., "data:image/png;base64,iVBORw0KG...")
     * @return a GeminiBlob containing the MIME type and base64 data
     * @throws IllegalArgumentException if the URI is not a valid data URI
     */
    private static GeminiBlob parseDataUri(URI uri) {
        String urlString = uri.toString();
        Matcher matcher = DATA_URI_PATTERN.matcher(urlString);

        if (matcher.matches()) {
            String mimeType = matcher.group(1);
            String base64Data = matcher.group(2);
            return new GeminiBlob(mimeType, base64Data);
        }

        throw new IllegalArgumentException("Invalid data URI format: " + urlString);
    }

    private static List<GeminiContent.GeminiPart> toGeminiParts(
            List<ToolExecutionRequest> toolExecutionRequests, String thoughtSignature) {
        List<GeminiContent.GeminiPart> geminiParts = new ArrayList<>();
        for (int i = 0; i < toolExecutionRequests.size(); i++) {
            ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(i);
            boolean shouldAddThoughtSignature = i == 0 && isNotNullOrEmpty(thoughtSignature);
            GeminiContent.GeminiPart geminiPart = GeminiContent.GeminiPart.builder()
                    .functionCall(new GeminiFunctionCall(
                            toolExecutionRequest.id(),
                            toolExecutionRequest.name(),
                            fromJson(toolExecutionRequest.arguments(), Map.class)))
                    .thoughtSignature(shouldAddThoughtSignature ? thoughtSignature : null)
                    .build();
            geminiParts.add(geminiPart);
        }
        return geminiParts;
    }
}
