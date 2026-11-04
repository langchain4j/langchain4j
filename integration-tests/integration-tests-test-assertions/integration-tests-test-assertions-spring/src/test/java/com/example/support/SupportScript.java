package com.example.support;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the mocked chat model behaves, depending on the user message:
 * <ul>
 *     <li>"cancel MS-123": calls getBookingDetails, then cancelBooking, then answers with the last tool result;</li>
 *     <li>"details MS-123": calls getBookingDetails;</li>
 *     <li>"refund MS-123 50": calls issueRefund;</li>
 *     <li>"split refund MS-123 30 20": calls getBookingDetails, then issueRefund twice;</li>
 *     <li>"summarize MS-123": calls getBookingDetails, then answers with a {@code BookingSummary} as JSON;</li>
 *     <li>"expert ...": calls askExpert, which calls the ExpertAssistant;</li>
 *     <li>"leak": answers with internal notes, which the output guardrail rejects;</li>
 *     <li>"boom": the model fails;</li>
 *     <li>a message augmented with retrieved contents: answers from them;</li>
 *     <li>anything else: a greeting.</li>
 * </ul>
 */
public final class SupportScript {

    public static final String GREETING = "Hello! How can I help you?";
    public static final String POLICY_ANSWER = "You can return the car free of charge within 14 days.";
    public static final String EXPERT_PREFIX = "Expert says: ";

    private static final String RAG_MARKER = "\n\nAnswer using the following information:";
    private static final Pattern CANCEL = Pattern.compile("cancel (\\S+)");
    private static final Pattern DETAILS = Pattern.compile("details (\\S+)");
    private static final Pattern REFUND = Pattern.compile("refund (\\S+) (\\d+)");
    private static final Pattern SPLIT_REFUND = Pattern.compile("split refund (\\S+) (\\d+) (\\d+)");
    private static final Pattern SUMMARIZE = Pattern.compile("summarize (\\S+)");

    private SupportScript() {}

    static AiMessage respond(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        String userText = lastUserMessage(messages);
        if (systemMessage(messages).contains("expert")) {
            return AiMessage.from(EXPERT_PREFIX + "ask for a manager.");
        }
        List<ToolExecutionResultMessage> toolResults = toolResultsAfterLastUserMessage(messages);
        List<ToolExecutionRequest> plan = plan(userText);
        if (toolResults.size() < plan.size()) {
            return AiMessage.from(plan.get(toolResults.size()));
        }
        Matcher summarize = SUMMARIZE.matcher(userText);
        if (!toolResults.isEmpty() && summarize.find()) {
            return AiMessage.from("{\"bookingNumber\":\"%s\",\"carType\":\"compact car\"}".formatted(summarize.group(1)));
        }
        if (!toolResults.isEmpty()) {
            return AiMessage.from("Done: " + toolResults.get(toolResults.size() - 1).text());
        }
        if (userText.contains("boom")) {
            throw new RuntimeException("model unavailable");
        }
        if (userText.contains("leak")) {
            return AiMessage.from("INTERNAL: this customer is flagged as difficult");
        }
        if (fullLastUserMessage(messages).contains(RAG_MARKER)) {
            return AiMessage.from(POLICY_ANSWER);
        }
        return AiMessage.from(GREETING);
    }

    private static List<ToolExecutionRequest> plan(String userText) {
        List<ToolExecutionRequest> plan = new ArrayList<>();
        Matcher cancel = CANCEL.matcher(userText);
        Matcher details = DETAILS.matcher(userText);
        Matcher refund = REFUND.matcher(userText);
        Matcher splitRefund = SPLIT_REFUND.matcher(userText);
        Matcher summarize = SUMMARIZE.matcher(userText);
        if (splitRefund.find()) {
            String bookingNumber = splitRefund.group(1);
            plan.add(toolCall("getBookingDetails", "{\"bookingNumber\":\"%s\"}".formatted(bookingNumber)));
            plan.add(toolCall(
                    "issueRefund",
                    "{\"bookingNumber\":\"%s\",\"amountInEuros\":%s}".formatted(bookingNumber, splitRefund.group(2))));
            plan.add(toolCall(
                    "issueRefund",
                    "{\"bookingNumber\":\"%s\",\"amountInEuros\":%s}".formatted(bookingNumber, splitRefund.group(3))));
        } else if (summarize.find()) {
            plan.add(toolCall("getBookingDetails", "{\"bookingNumber\":\"%s\"}".formatted(summarize.group(1))));
        } else if (cancel.find()) {
            plan.add(toolCall("getBookingDetails", "{\"bookingNumber\":\"%s\"}".formatted(cancel.group(1))));
            plan.add(toolCall("cancelBooking", "{\"bookingNumber\":\"%s\"}".formatted(cancel.group(1))));
        } else if (details.find()) {
            plan.add(toolCall("getBookingDetails", "{\"bookingNumber\":\"%s\"}".formatted(details.group(1))));
        } else if (refund.find()) {
            plan.add(toolCall(
                    "issueRefund",
                    "{\"bookingNumber\":\"%s\",\"amountInEuros\":%s}".formatted(refund.group(1), refund.group(2))));
        } else if (userText.startsWith("expert")) {
            plan.add(toolCall("askExpert", "{\"question\":\"%s\"}".formatted(userText.substring("expert".length()).trim())));
        }
        return plan;
    }

    private static ToolExecutionRequest toolCall(String name, String arguments) {
        return ToolExecutionRequest.builder()
                .id(name + "-call")
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static String systemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(message -> ((SystemMessage) message).text())
                .findFirst()
                .orElse("");
    }

    private static String fullLastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                return userMessage.singleText();
            }
        }
        return "";
    }

    private static String lastUserMessage(List<ChatMessage> messages) {
        String text = fullLastUserMessage(messages);
        int ragMarker = text.indexOf(RAG_MARKER);
        return ragMarker < 0 ? text : text.substring(0, ragMarker);
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
