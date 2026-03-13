package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.bedrock.AwsDocumentConverter.documentFromJson;
import static dev.langchain4j.model.bedrock.Utils.extractAndValidateFormat;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static software.amazon.awssdk.core.SdkBytes.fromByteArray;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentSource;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Shared static utility methods for converting langchain4j message types
 * to AWS Bedrock Converse API content blocks.
 */
@Internal
class BedrockMessageConverter {

    private BedrockMessageConverter() {}

    static ContentBlock convertContent(Content content) {
        if (content instanceof TextContent text) {
            return ContentBlock.builder().text(text.text()).build();
        } else if (content instanceof PdfFileContent pdfFileContent) {
            final SdkBytes bytes = fromByteArray(
                    nonNull(pdfFileContent.pdfFile().base64Data())
                            ? Base64.getDecoder()
                                    .decode(pdfFileContent.pdfFile().base64Data())
                            : readBytes(String.valueOf(pdfFileContent.pdfFile().url())));
            return ContentBlock.builder()
                    .document(DocumentBlock.builder()
                            .format(DocumentFormat.PDF)
                            .source(DocumentSource.builder().bytes(bytes).build())
                            .name(extractFilenameWithoutExtensionFromUri(
                                    pdfFileContent.pdfFile().url()))
                            .build())
                    .build();
        } else if (content instanceof ImageContent image) {
            return createImageBlock(image);
        }
        throw new IllegalArgumentException("Unsupported content type: " + content.getClass());
    }

    static List<ContentBlock> convertContents(List<Content> contents) {
        if (isNullOrEmpty(contents)) {
            return emptyList();
        }
        return contents.stream().map(BedrockMessageConverter::convertContent).toList();
    }

    static ContentBlock createImageBlock(ImageContent imageContent) {
        final SdkBytes bytes = fromByteArray(
                nonNull(imageContent.image().base64Data())
                        ? Base64.getDecoder().decode(imageContent.image().base64Data())
                        : readBytes(String.valueOf(imageContent.image().url())));
        final String imgFormat = extractAndValidateFormat(imageContent.image());
        return ContentBlock.builder()
                .image(ImageBlock.builder()
                        .format(imgFormat)
                        .source(ImageSource.builder().bytes(bytes).build())
                        .build())
                .build();
    }

    static ContentBlock createToolResultBlock(ToolExecutionResultMessage toolResult) {
        ToolResultBlock.Builder resultBuilder = ToolResultBlock.builder()
                .toolUseId(toolResult.id())
                .content(ToolResultContentBlock.builder()
                        .text(toolResult.text())
                        .build());
        if (Boolean.TRUE.equals(toolResult.isError())) {
            resultBuilder.status(ToolResultStatus.ERROR);
        }
        return ContentBlock.builder().toolResult(resultBuilder.build()).build();
    }

    static List<ContentBlock> convertToolRequests(List<ToolExecutionRequest> requests) {
        return requests.stream()
                .map(req -> ContentBlock.builder()
                        .toolUse(ToolUseBlock.builder()
                                .name(req.name())
                                .toolUseId(req.id())
                                .input(documentFromJson(req.arguments()))
                                .build())
                        .build())
                .toList();
    }

    static String extractFilenameWithoutExtensionFromUri(URI uri) {
        String extractedCleanFileName = Utils.extractCleanFileName(uri);
        if (isNullOrEmpty(extractedCleanFileName)) {
            extractedCleanFileName = UUID.randomUUID().toString();
        }
        return extractedCleanFileName;
    }
}
