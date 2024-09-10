package dev.langchain4j.model.googleai;

import com.google.gson.Gson;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.TextFileContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.internal.CustomMimeTypesFileTypeDetector;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

class PartsAndContentsMapper {

    private static final CustomMimeTypesFileTypeDetector mimeTypeDetector =
        new CustomMimeTypesFileTypeDetector();

    private static final Gson GSON = new Gson();

    static GeminiPart fromContentToGPart(Content content) {
        if (content.type().equals(ContentType.TEXT)) {
            TextContent textContent = (TextContent) content;

            return GeminiPart.builder()
                .text(textContent.text())
                .build();
        }  else if (content.type().equals(ContentType.TEXT_FILE)) {
            TextFileContent textFileContent = (TextFileContent) content;

            URI uri = textFileContent.textFile().url();
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
                        .mimeType(textFileContent.textFile().mimeType())
                        .data(textFileContent.textFile().base64Data())
                        .build())
                    .build();
            }

        } else if (content.type().equals(ContentType.IMAGE)) {
            ImageContent imageContent = (ImageContent) content;

            URI uri = imageContent.image().url();
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
                        .mimeType(imageContent.image().mimeType())
                        .data(imageContent.image().base64Data())
                        .build())
                    .build();
            }
//            Base64.getEncoder().encode(readBytes(imageContent.image().url()));


        } else if (content.type().equals(ContentType.AUDIO)) {
            AudioContent audioContent = (AudioContent) content;

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
        } else if (content.type().equals(ContentType.VIDEO)) {
                VideoContent videoContent = (VideoContent) content;

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
        } else if (content.type().equals(ContentType.PDF)) {
            PdfFileContent pdfFileContent = (PdfFileContent) content;

            URI uri = pdfFileContent.pdfFile().url();
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
                        .mimeType("application/pdf")
                        .data(pdfFileContent.pdfFile().base64Data())
                        .build())
                    .build();
            }
        } else {
            //TODO return null? throw error?
            return GeminiPart.builder().text("Error: Unknown content type").build();
        }
    }

    static AiMessage fromGPartsToAiMessage(List<GeminiPart> parts, boolean includeCodeExecutionOutput) {
        StringBuilder fullText = new StringBuilder();
        List<GeminiFunctionCall> functionCalls = new ArrayList<>();

        for (GeminiPart part : parts) {
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
            if (text != null && !text.isEmpty()) {
                fullText.append(text);
            }

            if (part.getFunctionCall() != null) {
                functionCalls.add(part.getFunctionCall());
            }
        }

        if (functionCalls.isEmpty()) {
            return AiMessage.from(fullText.toString());
        } else {
            return AiMessage.from(FunctionMapper.fromToolExecReqToGFunCall(functionCalls));
        }
    }

    static List<GeminiContent> fromMessageToGContent(List<ChatMessage> messages, GeminiContent systemInstruction) {
        return messages.stream()
            .map(msg -> {
                switch (msg.type()) {
                    case SYSTEM:
                        SystemMessage systemMessage = (SystemMessage) msg;

                        if (systemInstruction != null) {
                            systemInstruction.addPart(GeminiPart.builder()
                                .text(systemMessage.text())
                                .build());
                        }

                        return null;
                    case AI:
                        AiMessage aiMessage = (AiMessage) msg;

                        if (aiMessage.hasToolExecutionRequests()) {
                            return GeminiContent.builder()
                                .role(GeminiRole.MODEL.toString())
                                .parts(((AiMessage) msg).toolExecutionRequests().stream()
                                    .map(toolExecutionRequest -> GeminiPart.builder()
                                        .functionResponse(GeminiFunctionResponse.builder()
                                            .name(toolExecutionRequest.name())
                                            .response(GSON.fromJson(toolExecutionRequest.arguments(), Map.class))
                                            .build())
                                        .build())
                                    .collect(Collectors.toList()))
                                .build();
                        } else {
                            return GeminiContent.builder()
                                .role(GeminiRole.MODEL.toString())
                                .parts(singletonList(fromContentToGPart(TextContent.from(aiMessage.text()))))
                                .build();
                        }

                    case USER:
                        UserMessage userMessage = (UserMessage) msg;

                        return GeminiContent.builder()
                            .role(GeminiRole.USER.toString())
                            .parts(userMessage.contents().stream()
                                .map(PartsAndContentsMapper::fromContentToGPart)
                                .collect(Collectors.toList())
                            )
                            .build();
                    case TOOL_EXECUTION_RESULT:
                        ToolExecutionResultMessage toolResultMessage = (ToolExecutionResultMessage) msg;

                        return GeminiContent.builder()
                            .role(GeminiRole.USER.toString())
                            .parts(singletonList(fromContentToGPart(TextContent.from(toolResultMessage.text()))))
                            .build();
                    default:
                        return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
