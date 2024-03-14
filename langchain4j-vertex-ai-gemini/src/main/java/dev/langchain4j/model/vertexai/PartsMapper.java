package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.api.FunctionResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.cloud.vertexai.generativeai.PartMaker.fromMimeTypeAndData;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.readBytes;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

class PartsMapper {

    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = new HashMap<>();

    static {
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
    }

    static List<Part> map(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;

            if (aiMessage.hasToolExecutionRequests()) {
                return singletonList(Part.newBuilder()
                    .setFunctionCall(
                        //TODO: handling one function call, but can there be several?

                        FunctionCallHelper.fromToolExecutionRequest(aiMessage.toolExecutionRequests().get(0))
                    )
                    .build());
            } else {
                return singletonList(Part.newBuilder()
                    .setText(aiMessage.text())
                    .build());
            }
        } else
        if (message instanceof UserMessage) {
            return ((UserMessage) message).contents().stream()
                .map(PartsMapper::map)
                .collect(toList());
        } else if (message instanceof ToolExecutionResultMessage) {
            ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
            String functionResponseText = toolExecutionResultMessage.text();

            Struct.Builder structBuilder = Struct.newBuilder();
            try {
                JsonFormat.parser().merge(functionResponseText, structBuilder);
            } catch (InvalidProtocolBufferException e) {
                // TODO do a proper fix
                String functionResponseTextAsMap = "{\"result\":" + functionResponseText + "}";
                try {
                    JsonFormat.parser().merge(functionResponseTextAsMap, structBuilder);
                } catch (InvalidProtocolBufferException e2) {
                    throw new RuntimeException(e);
                }
            }
            Struct responseStruct = structBuilder.build();

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

    private static Part map(Content content) {
        if (content instanceof TextContent) {
            return map((TextContent) content);
        } else if (content instanceof ImageContent) {
            return map((ImageContent) content);
        } else {
            throw illegalArgument("Unknown content type: " + content);
        }
    }

    private static Part map(TextContent content) {
        return Part.newBuilder()
                .setText(content.text())
                .build();
    }

    static Part map(ImageContent content) {
        Image image = content.image();
        if (image.url() != null) {
            String mimeType = getOrDefault(image.mimeType(), () -> detectMimeType(image.url()));
            if (image.url().getScheme().equals("gs")) {
                return fromMimeTypeAndData(mimeType, image.url());
            } else {
                return fromMimeTypeAndData(mimeType, readBytes(image.url().toString()));
            }
        }
        return fromMimeTypeAndData(image.mimeType(), Base64.getDecoder().decode(image.base64Data()));
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
}
