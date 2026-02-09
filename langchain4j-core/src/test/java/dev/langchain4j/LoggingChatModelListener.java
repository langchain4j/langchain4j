package dev.langchain4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
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
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.stream.Collectors.joining;

public class LoggingChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatModelListener.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
    }

    private static String format(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return "SYSTEM: " + systemMessage.text();
        } else if (message instanceof UserMessage userMessage) {
            return "USER: " + format(userMessage);
        } else if (message instanceof AiMessage aiMessage) {
            return "AI: " + format(aiMessage);
        } else if (message instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return "TOOL: " + toolExecutionResultMessage.text();
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message.getClass());
        }
    }

    private static String format(UserMessage userMessage) {
        if (userMessage.hasSingleText()) {
            return userMessage.singleText();
        }
        return userMessage.contents().stream()
                .map(content -> {
                    if (content instanceof TextContent textContent) {
                        return textContent.text();
                    } else if (content instanceof ImageContent imageContent) {
                        return "[IMAGE]";
                    } else if (content instanceof AudioContent audioContent) {
                        return "[AUDIO]";
                    } else if (content instanceof VideoContent videoContent) {
                        return "[VIDEO]";
                    } else if (content instanceof PdfFileContent pdfFileContent) {
                        return "[PDF_FILE]";
                    } else {
                        throw new IllegalArgumentException("Unknown content type: " + content.getClass());
                    }
                }).collect(joining(" "));
    }

    private static String format(AiMessage aiMessage) {
        StringBuilder sb = new StringBuilder();

        if (!isNullOrBlank(aiMessage.text())) {
            sb.append(aiMessage.text());
            sb.append(" ");
        }

        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
            sb
                    .append("[")
                    .append(toolExecutionRequest.name())
                    .append("(")
                    .append(printArguments(toolExecutionRequest.arguments()))
                    .append(")] ");
        }
        return sb.toString();
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatRequest chatRequest = responseContext.chatRequest();
        ChatResponse chatResponse = responseContext.chatResponse();

        StringBuilder sb = new StringBuilder();
        List<ChatMessage> messages = chatRequest.messages();
        messages.forEach(message -> {
            sb.append(format(message));
            sb.append("\n\n");
        });

        log.info("""
                        >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
                        {}TOOLS: {}
                        ------------------------------------------------------------------------------------------
                        {}
                        <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
                        
                        """,
                sb,
                chatRequest.toolSpecifications().stream().map(ToolSpecification::name).collect(joining(", ")),
                "AI: " + format(chatResponse.aiMessage())
        );
    }

    private static String printArguments(String json) {
        JsonNode root = null;
        try {
            root = mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(root.fields(), 0), false)
                .sorted(Comparator.comparingInt(e ->
                        Integer.parseInt(e.getKey().substring(3)))) // arg0, arg1, ...
                .map(e -> e.getValue().isTextual()
                        ? "\"" + e.getValue().asText() + "\""
                        : e.getValue().toString())
                .collect(Collectors.joining(", "));
    }
}
