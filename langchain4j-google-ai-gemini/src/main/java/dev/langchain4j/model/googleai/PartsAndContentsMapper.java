package dev.langchain4j.model.googleai;

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

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.googleai.FunctionMapper.toToolExecutionRequests;
import static dev.langchain4j.model.googleai.Json.fromJson;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

class PartsAndContentsMapper {

    static final String THINKING_SIGNATURE_KEY = "thinking_signature"; // do not change, will break backward compatibility!

    private static final CustomMimeTypesFileTypeDetector mimeTypeDetector =
        new CustomMimeTypesFileTypeDetector();

    static GeminiPart fromContentToGPart(Content content) {
        if (content instanceof TextContent textContent) {
            return GeminiPart.builder()
                .text(textContent.text())
                .build();
        } else if (content instanceof ImageContent imageContent) {
            Image image = imageContent.image();

            if (!isNullOrBlank(image.base64Data())) {
                return GeminiPart.builder()
                        .inlineData(GeminiBlob.builder()
                                .mimeType(image.mimeType())
                                .data(image.base64Data())
                                .build())
                        .build();
            } else if (image.url() != null) {
                URI url = image.url();
                if (url.getScheme() != null && url.getScheme().startsWith("http")) {
                    byte[] imageBytes = readBytes(url.toString());
                    String base64Data = Base64.getEncoder().encodeToString(imageBytes);
                    return GeminiPart.builder()
                            .inlineData(GeminiBlob.builder()
                                    .mimeType(getOrDefault(image.mimeType(), mimeTypeDetector.probeContentType(url)))
                                    .data(base64Data)
                                    .build())
                            .build();
                } else {
                    return GeminiPart.builder()
                            .fileData(GeminiFileData.builder()
                                    .fileUri(url.toString())
                                    .mimeType(getOrDefault(image.mimeType(), mimeTypeDetector.probeContentType(url)))
                                    .build())
                            .build();
                }
            } else {
                throw new IllegalArgumentException("Image should contain either base64 data or url");
            }
        } else if (content instanceof AudioContent audioContent) {
            URI uri = audioContent.audio().url();
            if (uri != null) {
                return GeminiPart.builder()
                    .fileData(GeminiFileData.builder()
                        .fileUri(uri.toString())
                        .mimeType(mimeTypeDetector.probeContentType(uri))
                        .build())
                    .build();
            } else {
                return GeminiPart.builder()
                    .inlineData(GeminiBlob.builder()
                        .mimeType(audioContent.audio().mimeType())
                        .data(audioContent.audio().base64Data())
                        .build())
                    .build();
            }
        } else if (content instanceof VideoContent videoContent) {
                URI uri = videoContent.video().url();
                if (uri != null) {
                    return GeminiPart.builder()
                        .fileData(GeminiFileData.builder()
                            .fileUri(uri.toString())
                            .mimeType(mimeTypeDetector.probeContentType(uri))
                            .build())
                        .build();
                } else {
                    return GeminiPart.builder()
                        .inlineData(GeminiBlob.builder()
                            .mimeType(videoContent.video().mimeType())
                            .data(videoContent.video().base64Data())
                            .build())
                        .build();
                }
        } else if (content instanceof PdfFileContent pdfFileContent) {
            PdfFile pdfFile = pdfFileContent.pdfFile();

            URI uri = pdfFile.url();
            if (uri != null) {
                return GeminiPart.builder()
                    .fileData(GeminiFileData.builder()
                        .fileUri(uri.toString())
                        .mimeType(mimeTypeDetector.probeContentType(uri))
                        .build())
                    .build();
            } else {
                return GeminiPart.builder()
                    .inlineData(GeminiBlob.builder()
                        .mimeType(pdfFile.mimeType())
                        .data(pdfFile.base64Data())
                        .build())
                    .build();
            }
        } else {
            throw new UnsupportedFeatureException("Unsupported content type: " + content.type());
        }
    }

    static AiMessage fromGPartsToAiMessage(List<GeminiPart> parts,
                                           boolean includeCodeExecutionOutput,
                                           Boolean returnThinking) {

        List<GeminiPart> safeParts = Objects.requireNonNullElse(parts, List.of());
        StringBuilder fullText = new StringBuilder();
        List<String> thoughts = new ArrayList<>();
        List<String> thoughtSignatures = new ArrayList<>();
        List<GeminiFunctionCall> functionCalls = new ArrayList<>();

        for (GeminiPart part : safeParts) {
            GeminiExecutableCode executableCode = part.getExecutableCode();
            if (executableCode != null && includeCodeExecutionOutput) {
                fullText
                    .append("Code executed:\n")
                    .append("```python")
                    .append(executableCode.getProgrammingLanguage() != null ? //TODO check below why programming language is null
                        executableCode.getProgrammingLanguage().toString() :
                        "")
                    .append(executableCode.getCode())
                    .append("```\n");
            }

            GeminiCodeExecutionResult codeExecutionResult = part.getCodeExecutionResult();
            if (codeExecutionResult != null && includeCodeExecutionOutput) {
                GeminiOutcome outcome = codeExecutionResult.getOutcome();

                if (outcome != GeminiOutcome.OUTCOME_OK) {
                    fullText
                        .append("Code execution failed: **")
                        .append(outcome.name())
                        .append("**\n")
                        .append(part.getText() != null ? part.getText() : "");
                } else {
                    fullText
                        .append("Output:\n")
                        .append("```\n")
                        .append(codeExecutionResult.getOutput())
                        .append("```\n");
                }
            }

            String text = part.getText();
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

            String thoughtSignature = part.getThoughtSignature();
            if (Boolean.TRUE.equals(returnThinking) && isNotNullOrEmpty(thoughtSignature)) {
                thoughtSignatures.add(thoughtSignature);
            }

            if (part.getFunctionCall() != null) {
                functionCalls.add(part.getFunctionCall());
            }
        }

        String text = fullText.toString();
        String thinking = thoughts.stream().collect(joining("\n\n"));
        String thinkingSignature = thoughtSignatures.stream().collect(joining("\n\n"));

        return AiMessage.builder()
                .text(isNullOrEmpty(text) ? null : text)
                .thinking(isNullOrEmpty(thinking) ? null : thinking)
                .toolExecutionRequests(toToolExecutionRequests(functionCalls))
                .attributes(isNullOrEmpty(thinkingSignature) ? null : Map.of(THINKING_SIGNATURE_KEY, thinkingSignature))
                .build();
    }

    static List<GeminiContent> fromMessageToGContent(List<ChatMessage> messages,
                                                     GeminiContent systemInstruction,
                                                     boolean sendThinking) {
        return messages.stream()
            .map(msg -> {
                switch (msg.type()) {
                    case SYSTEM:
                        SystemMessage systemMessage = (SystemMessage) msg;

                        if (systemInstruction != null) {
                            systemInstruction.addPart(GeminiPart.builder()
                                .text(systemMessage.text())
                                .build());
                            return null;
                        }

                        if (isNotNullOrEmpty(systemMessage.text())) {
                            return GeminiContent.builder()
                                .role(GeminiRole.MODEL.toString())
                                .parts(List.of(GeminiPart.builder()
                                    .text(systemMessage.text())
                                    .build()))
                                .build();
                        }

                        return null;
                    case AI:
                        AiMessage aiMessage = (AiMessage) msg;

                        List<GeminiPart> parts = new ArrayList<>();

                        if (sendThinking && isNotNullOrEmpty(aiMessage.thinking())) {
                            parts.add(GeminiPart.builder()
                                    .text(aiMessage.thinking())
                                    .thought(true)
                                    .build());
                        }

                        if (isNotNullOrEmpty(aiMessage.text())) {
                            parts.add(GeminiPart.builder()
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

                        return GeminiContent.builder()
                                .role(GeminiRole.MODEL.toString())
                                .parts(parts)
                                .build();

                    case USER:
                        UserMessage userMessage = (UserMessage) msg;

                        return GeminiContent.builder()
                            .role(GeminiRole.USER.toString())
                            .parts(userMessage.contents().stream()
                                .map(PartsAndContentsMapper::fromContentToGPart)
                                .collect(toList())
                            )
                            .build();
                    case TOOL_EXECUTION_RESULT:
                        ToolExecutionResultMessage toolResultMessage = (ToolExecutionResultMessage) msg;

                        return GeminiContent.builder()
                            .role(GeminiRole.USER.toString())
                            .parts(List.of(GeminiPart.builder()
                                    .functionResponse(GeminiFunctionResponse.builder()
                                            .name(toolResultMessage.toolName())
                                            .response(Map.of("response", toolResultMessage.text()))
                                            .build())
                                    .build()))
                            .build();
                    default:
                        return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(toList());
    }

    private static List<GeminiPart> toGeminiParts(List<ToolExecutionRequest> toolExecutionRequests,
                                                  String thoughtSignature) {
        List<GeminiPart> geminiParts = new ArrayList<>();
        for (int i = 0; i < toolExecutionRequests.size(); i++) {
            ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(i);
            boolean shouldAddThoughtSignature = i == 0 && isNotNullOrEmpty(thoughtSignature);
            GeminiPart geminiPart = GeminiPart.builder()
                    .functionCall(GeminiFunctionCall.builder()
                            .name(toolExecutionRequest.name())
                            .args(fromJson(toolExecutionRequest.arguments(), Map.class))
                            .build())
                    .thoughtSignature(shouldAddThoughtSignature ? thoughtSignature : null)
                    .build();
            geminiParts.add(geminiPart);
        }
        return geminiParts;
    }
}
