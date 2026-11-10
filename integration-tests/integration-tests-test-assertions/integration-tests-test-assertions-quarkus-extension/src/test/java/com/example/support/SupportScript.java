package com.example.support;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.test.mock.ChatModelMock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the mocked chat model behaves, depending on the last user message:
 * <ul>
 *     <li>"cancel MS-123": calls getBookingDetails, then cancelBooking, then answers with the last tool result;</li>
 *     <li>"details MS-123": calls getBookingDetails, then answers with its result;</li>
 *     <li>"boom": the model fails;</li>
 *     <li>a message augmented with retrieved contents: answers from them;</li>
 *     <li>"What is my name?": answers with the name found in an earlier user message of the conversation;</li>
 *     <li>anything else: a greeting.</li>
 * </ul>
 */
final class SupportScript {

    static final String GREETING = "Hello! How can I help you?";
    static final String POLICY_ANSWER = "You can return the car free of charge within 14 days.";

    private static final String RAG_MARKER = "\n\nAnswer using the following information:";
    private static final Pattern CANCEL = Pattern.compile("cancel (\\S+)");
    private static final Pattern DETAILS = Pattern.compile("details (\\S+)");
    private static final Pattern NAME = Pattern.compile("My name is (\\w+)");

    private SupportScript() {}

    static AiMessage respond(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        String fullUserText = lastUserMessage(messages);
        String userText = withoutRetrievedContents(fullUserText);
        List<ToolExecutionResultMessage> toolResults = toolResultsAfterLastUserMessage(messages);
        List<ToolExecutionRequest> plan = plan(userText);
        if (toolResults.size() < plan.size()) {
            return AiMessage.from(plan.get(toolResults.size()));
        }
        if (!toolResults.isEmpty()) {
            return AiMessage.from("Done: " + toolResults.get(toolResults.size() - 1).text());
        }
        if (userText.contains("boom")) {
            throw new RuntimeException("model unavailable");
        }
        if (fullUserText.contains(RAG_MARKER)) {
            return AiMessage.from(POLICY_ANSWER);
        }
        if (userText.contains("What is my name?")) {
            return AiMessage.from(earlierName(messages));
        }
        return AiMessage.from(GREETING);
    }

    private static List<ToolExecutionRequest> plan(String userText) {
        List<ToolExecutionRequest> plan = new ArrayList<>();
        Matcher cancel = CANCEL.matcher(userText);
        Matcher details = DETAILS.matcher(userText);
        if (cancel.find()) {
            plan.add(toolCall("getBookingDetails", cancel.group(1)));
            plan.add(toolCall("cancelBooking", cancel.group(1)));
        } else if (details.find()) {
            plan.add(toolCall("getBookingDetails", details.group(1)));
        }
        return plan;
    }

    private static ToolExecutionRequest toolCall(String name, String bookingNumber) {
        return ChatModelMock.toolCall(name, Map.of("bookingNumber", bookingNumber));
    }

    private static String earlierName(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                Matcher name = NAME.matcher(userMessage.singleText());
                if (name.find()) {
                    return "Your name is " + name.group(1) + ".";
                }
            }
        }
        return "I do not know your name.";
    }

    private static String lastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }
        return "";
    }

    private static String withoutRetrievedContents(String userText) {
        int ragMarker = userText.indexOf(RAG_MARKER);
        return ragMarker < 0 ? userText : userText.substring(0, ragMarker);
    }

    private static List<ToolExecutionResultMessage> toolResultsAfterLastUserMessage(List<ChatMessage> messages) {
        List<ToolExecutionResultMessage> results = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && !(messages.get(i) instanceof UserMessage); i--) {
            if (messages.get(i) instanceof ToolExecutionResultMessage result) {
                results.add(0, result);
            }
        }
        return results;
    }
}
