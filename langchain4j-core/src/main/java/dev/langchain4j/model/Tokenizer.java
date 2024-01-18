package dev.langchain4j.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationsFrom;
import static java.util.Collections.singletonList;

/**
 * Represents an interface for estimating the count of tokens in various text types such as a text, prompt, text segment, etc.
 * This can be useful when it's necessary to know in advance the cost of processing a specified text by the LLM.
 */
public interface Tokenizer {

    /**
     * Estimates the count of tokens in the given text.
     * @param text the text.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInText(String text);

    /**
     * Estimates the count of tokens in the given message.
     * @param message the message.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInMessage(ChatMessage message);

    /**
     * Estimates the count of tokens in the given messages.
     * @param messages the messages.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInMessages(Iterable<ChatMessage> messages);

    /**
     * Estimates the count of tokens in {@code Tool} annotations of the given object.
     * @param objectWithTools the object.
     * @return the estimated count of tokens.
     */
    default int estimateTokenCountInTools(Object objectWithTools) {
        return estimateTokenCountInTools(singletonList(objectWithTools));
    }

    /**
     * Estimates the count of tokens in {@code Tool} annotations of the given objects.
     * @param objectsWithTools the objects.
     * @return the estimated count of tokens.
     */
    default int estimateTokenCountInTools(Iterable<Object> objectsWithTools) {
        List<ToolSpecification> toolSpecifications = new ArrayList<>();
        objectsWithTools.forEach(objectWithTools ->
                toolSpecifications.addAll(toolSpecificationsFrom(objectWithTools)));
        return estimateTokenCountInToolSpecifications(toolSpecifications);
    }

    /**
     * Estimates the count of tokens in the given tool specifications.
     * @param toolSpecifications the tool specifications.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications);

    /**
     * Estimates the count of tokens in the given tool specification.
     * @param toolSpecification the tool specification.
     * @return the estimated count of tokens.
     */
    default int estimateTokenCountInForcefulToolSpecification(ToolSpecification toolSpecification) {
        return estimateTokenCountInToolSpecifications(singletonList(toolSpecification));
    }

    /**
     * Estimates the count of tokens in the given tool execution requests.
     * @param toolExecutionRequests the tool execution request.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests);

    /**
     * Estimates the count of tokens in the given tool execution request.
     * @param toolExecutionRequest the tool execution request.
     * @return the estimated count of tokens.
     */
    default int estimateTokenCountInForcefulToolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
        return estimateTokenCountInToolExecutionRequests(singletonList(toolExecutionRequest));
    }
}
