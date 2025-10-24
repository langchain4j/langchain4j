package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.googleai.FunctionMapper.toToolExecutionRequests;
import static dev.langchain4j.model.googleai.Json.fromJson;
import static java.util.stream.Collectors.joining;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PartsAndContentsMapper {
    private PartsAndContentsMapper() {}

    static final String THINKING_SIGNATURE_KEY =
            "thinking_signature"; // do not change, will break backward compatibility!
    static final String GENERATED_IMAGES_KEY =
            "generated_images"; // key for storing generated images in AiMessage attributes

    private static final CustomMimeTypesFileTypeDetector mimeTypeDetector = new CustomMimeTypesFileTypeDetector();

    static GeminiContent.GeminiPart fromContentToGPart(Content content) {
        if (content instanceof TextContent textContent) {
            return GeminiContent.GeminiPart.builder().text(textContent.text()).build();
        } else if (content instanceof ImageContent imageContent) {
            Image image = imageContent.image();

            if (!isNullOrBlank(image.base64Data())) {
                return GeminiContent.GeminiPart.builder()
                        .inlineData(new GeminiBlob(image.mimeType(), image.base64Data()))
                        .build();
            } else if (image.url() != null) {
                URI url = image.url();
                if (url.getScheme() != null && url.getScheme().startsWith("http")) {
                    byte[] imageBytes = readBytes(url.toString());
                    String base64Data = Base64.getEncoder().encodeToString(imageBytes);
                    return GeminiContent.GeminiPart.builder()
                            .inlineData(new GeminiBlob(
                                    getOrDefault(image.mimeType(), mimeTypeDetector.probeContentType(url)), base64Data))
                            .build();
                } else {
                    return GeminiContent.GeminiPart.builder()
                            .fileData(new GeminiFileData(
                                    getOrDefault(image.mimeType(), mimeTypeDetector.probeContentType(url)),
                                    url.toString()))
                            .build();
                }
            } else {
                throw new IllegalArgumentException("Image should contain either base64 data or url");
            }
        } else if (content instanceof AudioContent audioContent) {
            URI uri = audioContent.audio().url();
            if (uri != null) {
                return GeminiContent.GeminiPart.builder()
                        .fileData(new GeminiFileData(mimeTypeDetector.probeContentType(uri), uri.toString()))
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
                return GeminiContent.GeminiPart.builder()
                        .fileData(new GeminiFileData(mimeTypeDetector.probeContentType(uri), uri.toString()))
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
                return GeminiContent.GeminiPart.builder()
                        .fileData(new GeminiFileData(mimeTypeDetector.probeContentType(uri), uri.toString()))
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
                        .append("```python")
                        .append(
                                executableCode.programmingLanguage() != null
                                        ? // TODO check below why programming language is null
                                        // TODO: Is this correct? This would result in: ```pythonpythonCODE```
                                        executableCode.programmingLanguage().toString()
                                        : "")
                        .append(executableCode.code())
                        .append("```\n");
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
            List<ChatMessage> messages, GeminiContent systemInstruction, boolean sendThinking) {
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
                                            .map(PartsAndContentsMapper::fromContentToGPart)
                                            .toList(),
                                    GeminiRole.USER.toString());
                        case TOOL_EXECUTION_RESULT:
                            ToolExecutionResultMessage toolResultMessage = (ToolExecutionResultMessage) msg;

                            return new GeminiContent(
                                    List.of(GeminiContent.GeminiPart.builder()
                                            .functionResponse(new GeminiFunctionResponse(
                                                    toolResultMessage.toolName(),
                                                    Map.of("response", toolResultMessage.text())))
                                            .build()),
                                    GeminiRole.USER.toString());
                        default:
                            return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<GeminiContent.GeminiPart> toGeminiParts(
            List<ToolExecutionRequest> toolExecutionRequests, String thoughtSignature) {
        List<GeminiContent.GeminiPart> geminiParts = new ArrayList<>();
        for (int i = 0; i < toolExecutionRequests.size(); i++) {
            ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(i);
            boolean shouldAddThoughtSignature = i == 0 && isNotNullOrEmpty(thoughtSignature);
            GeminiContent.GeminiPart geminiPart = GeminiContent.GeminiPart.builder()
                    .functionCall(new GeminiFunctionCall(
                            toolExecutionRequest.name(), fromJson(toolExecutionRequest.arguments(), Map.class)))
                    .thoughtSignature(shouldAddThoughtSignature ? thoughtSignature : null)
                    .build();
            geminiParts.add(geminiPart);
        }
        return geminiParts;
    }
}
