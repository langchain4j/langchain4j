package dev.langchain4j.model.watsonx.internal.api.requests;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.data.message.ContentType.AUDIO;
import static dev.langchain4j.data.message.ContentType.PDF;
import static dev.langchain4j.data.message.ContentType.TEXT_FILE;
import static dev.langchain4j.data.message.ContentType.VIDEO;

public class WatsonxChatMessageConverter {

    private static final String ASSISTANT = "assistant";
    private static final String USER = "user";
    private static final String SYSTEM = "system";
    private static final String TOOL = "tool";

    private WatsonxChatMessageConverter() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this class");
    }


    public static WatsonxChatMessage convert(ChatMessage chatMessage) {
        return switch (chatMessage.type()) {
            case AI -> build((AiMessage) chatMessage);
            case SYSTEM -> build((SystemMessage) chatMessage);
            case USER -> build((UserMessage) chatMessage);
            case TOOL_EXECUTION_RESULT -> build((ToolExecutionResultMessage) chatMessage);
        };
    }


    private static WatsonxChatMessage build(AiMessage aiMessage) {
        List<WatsonxTextChatToolCall> toolCalls = Optional.ofNullable(aiMessage.toolExecutionRequests())
            .map(requests -> requests.stream()
                .map(WatsonxTextChatToolCall::of)
                .toList())
            .orElse(null);

        return new WatsonxChatMessageAssistant(ASSISTANT, aiMessage.text(), toolCalls);
    }



    private static WatsonxChatMessage build(UserMessage userMessage) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Content content : userMessage.contents()) {
            contents.add(buildContentMap(content));
        }
        return new WatsonxChatMessageUser(USER, contents, userMessage.name());
    }


    private static WatsonxChatMessage build(SystemMessage systemMessage) {
        return new WatsonxChatMessageSystem(SYSTEM, systemMessage.text());
    }

    private static WatsonxChatMessage build(ToolExecutionResultMessage toolExecutionResultMessage) {
        return new WatsonxChatMessageTool(TOOL, toolExecutionResultMessage.text(), toolExecutionResultMessage.id());
    }

    private static Map<String, Object> buildContentMap(Content content) {
        if (content instanceof TextContent textContent) {
            return Map.of(
                "type", "text",
                "text", textContent.text()
            );
        } else if (content instanceof ImageContent imageContent) {
            String base64 = "data:image/%s;base64,%s".formatted(
                imageContent.image().mimeType(),
                imageContent.image().base64Data()
            );
            return Map.of(
                "type", "image_url",
                "image_url", Map.of(
                    "url", base64,
                    "detail", imageContent.detailLevel().name().toLowerCase()
                )
            );
        } else if (content.type() == AUDIO ||
            content.type() == PDF ||
            content.type() == TEXT_FILE ||
            content.type() == VIDEO) {
            throw new UnsupportedOperationException("Unimplemented case: " + content.type());
        } else {
            throw new IllegalArgumentException("Unknown content type: " + content.type());
        }
    }
}
