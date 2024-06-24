package dev.langchain4j.model.ollama;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.langchain4j.model.ollama.ExperimentalMessagesUtils.*;

/**
 * This class simulates tools function on ollama server.
 * To do that, it creates a specific request on top of the user query.
 * This request ask LLM to retrieve a list of tools to use to answer to the user query and the associated response.
 * The tools come from the Tool registered to the AiService.
 * Tools inputs and the response can reference tools results. So in one call to LLM we provide the answer.
 */
@Experimental
class ExperimentalParallelToolsDelegate implements ChatLanguageModel {
    static final PromptTemplate DEFAULT_SYSTEM_TEMPLATE = PromptTemplate
            .from("""
                    --- Context ---
                    {{context}}
                    ---------------

                    You are a helpful AI assistant responding to user requests taking into account the previous context.
                    You have access to the following tools, and only those tools:
                    {{tools}}
                    
                    Respond with a JSON object containing "tools" and required "response" fields:
                        - "tools": if required, the list of tools from the provided list in JSON format, containing:
                            - "name": selected tool name.
                            - "inputs": selected tool parameters matching the selected tool's JSON schema.
                            - "result_id": an id to identify the result of this tool, e.g., id1.
                        - "response": required String representing the conclusion with your answer using tools results
                              reference including last action done if needed. Ex:  "response" = "The final result is $(id3)".

                    Guidelines:
                        - If you have enough information, answer to it directly.
                        - Break complex request down into full sequential list of tools to be able to answer precisely in the response field.
                        - You cannot use tools that are not listed in the provided list.
                        - You should always provide a response field.
                        - Prefer using previous result.
                        - Inputs and response can reference previous results using $(xxx), where xxx is a previous unique result_id, Ex: "inputs" = { "arg0": $(id1) }.
                    """);

    record ToolResponses(List<ToolResponse> tools, String response) {
    }

    record ToolResponse(String name, Map<String, Object> inputs, String result_id) {
    }

    private final OllamaClient client;
    private final String modelName;
    private final Options options;

    ExperimentalParallelToolsDelegate(OllamaClient client, String modelName, Options options) {
        this.client = client;
        this.modelName = modelName;
        this.options = options;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .options(options)
                .stream(false)
                .messages(OllamaMessagesUtils.toOllamaMessages(messages))
                .build();
        ChatResponse response = client.chat(request);
        AiMessage aiMessage = AiMessage.from(response.getMessage().getContent());
        return Response.from(aiMessage, new TokenUsage(response.getPromptEvalCount(), response.getEvalCount()));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages,
                                        List<ToolSpecification> toolSpecifications) {
        ChatRequest.ChatRequestBuilder builder = ChatRequest.builder()
                .model(modelName)
                .options(options)
                .format("json")
                .stream(false);

        List<Message> ollamaMessages = toOllamaGroupedMessages(messages);

        if (hasAiStatsMessage(messages)) {
            AiStatsMessage aiStatsMessage = toAiStatsMessage(messages);
            if (aiStatsMessage.text() == null ) {
                throw new RuntimeException("Response cannot be null or empty!");
            }
            return Response.from(withoutRequests(aiStatsMessage), aiStatsMessage.getTokenUsage(), FinishReason.STOP);
        }

        Message systemMessage = createSystemMessageWithTools(ollamaMessages, toolSpecifications);

        List<Message> otherMessages = ollamaMessages.stream().filter(om -> om.getRole() != Role.SYSTEM).toList();
        List<Message> messagesWithTools = new ArrayList<>(otherMessages.size() + 1);
        messagesWithTools.add(systemMessage);
        messagesWithTools.addAll(otherMessages);

        builder.messages(messagesWithTools);

        ChatResponse response = client.chat(builder.build());
        ToolResponses toolResponses;
        try {
            toolResponses = Json.fromJson(response.getMessage().getContent(), ToolResponses.class);
        } catch (Exception e) {
            throw new RuntimeException("Ollama server did not respond with valid JSON. Please try again!");
        }

        TokenUsage tokenUsage = new TokenUsage(response.getPromptEvalCount(), response.getEvalCount());
        AiMessage aiMessage;

        if (toolResponses.response != null && (toolResponses.tools == null || toolResponses.tools.isEmpty())) {
            aiMessage = AiMessage.from(toolResponses.response);
        } else {
            List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
            List<String> availableTools = toolSpecifications.stream().map(ToolSpecification::name).toList();

            for (ToolResponse toolResponse : toolResponses.tools) {
                if (!availableTools.contains(toolResponse.name)) {
                    throw new RuntimeException(String.format(
                            "Ollama server wants to call a name '%s' that is not part of the available tools %s",
                            toolResponse.name, availableTools));
                } else {
                    getToolSpecification(toolResponse, toolSpecifications)
                            .map(ts -> toToolExecutionRequest(toolResponse, ts))
                            .ifPresent(toolExecutionRequests::add);
                }
            }
            if (toolResponses.response != null && !toolResponses.response().isEmpty()) {
                aiMessage = new AiMessage(toolResponses.response, toolExecutionRequests);
            } else {
                aiMessage = AiMessage.from(toolExecutionRequests);
            }
        }
        return Response.from(AiStatsMessage.from(aiMessage, tokenUsage), tokenUsage);
    }

    private Message createSystemMessageWithTools(List<Message> messages, List<ToolSpecification> toolSpecifications) {
        String initialSystemMessages = messages.stream().filter(sm -> sm.getRole() == Role.SYSTEM)
                .map(Message::getContent)
                .collect(Collectors.joining("\n"));
        List<ToolSpecification> tools = new ArrayList<>(toolSpecifications);
        Prompt prompt = DEFAULT_SYSTEM_TEMPLATE.apply(
                Map.of("tools", Json.toJson(tools),
                        "context", initialSystemMessages)
        );
        return Message.builder()
                .role(Role.SYSTEM)
                .content(prompt.text())
                .build();
    }

    private Optional<ToolSpecification> getToolSpecification(ToolResponse toolResponse,
                                                             List<ToolSpecification> toolSpecifications) {
        return toolSpecifications.stream()
                .filter(ts -> ts.name().equals(toolResponse.name))
                .findFirst();
    }

    private static ToolExecutionRequest toToolExecutionRequest(
            ToolResponse toolResponse, ToolSpecification toolSpecification) {
        return ToolExecutionRequest.builder()
                .id(toolResponse.result_id)
                .name(toolSpecification.name())
                .arguments(Json.toJson(toolResponse.inputs))
                .build();
    }

    static class AiStatsMessage extends AiMessage {

        final TokenUsage tokenUsage;

        AiStatsMessage(String text, TokenUsage tokenUsage) {
            super(text);
            this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
        }

        AiStatsMessage(List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
            super(toolExecutionRequests);
            this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
        }

        AiStatsMessage(String text, List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
            super(text, toolExecutionRequests);
            this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokenUsage");
        }

        TokenUsage getTokenUsage() {
            return tokenUsage;
        }

        static AiStatsMessage from(AiMessage aiMessage, TokenUsage tokenUsage) {
            if (aiMessage.text() == null) {
                return new AiStatsMessage(aiMessage.toolExecutionRequests(), tokenUsage);
            } else {
                return new AiStatsMessage(aiMessage.text(), aiMessage.toolExecutionRequests(), tokenUsage);
            }
        }
    }
}