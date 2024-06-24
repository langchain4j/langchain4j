package dev.langchain4j.model.ollama;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class simulates tools function on ollama server.
 * It creates a specific request to ask llm if a tool needs to be called before answering,
 * then it execute the tool and ask again to llm to answer or if a new tool call is needed.
 * It requests tools sequentially and use the history to give the final answer.
 */
@Experimental
class ExperimentalSequentialToolsDelegate implements ChatLanguageModel {

    static final PromptTemplate DEFAULT_SYSTEM_TEMPLATE = PromptTemplate.from("""
            You are a helpful AI assistant responding to user requests.
            {{context}}
            You have access to the following tools, and only those tools:
            {{tools}}
            
            Break down complex request into sequential unitary tool calls.
            Use previous messages to avoid asking twice the same tool and select the most appropriate tool.
            
            You can not select the same tool with same properties twice.
            , use "__conversational_response" tool.
            When you have gathered enough information or if tool have succeed, use "__conversational_response" tool.
                Ex: {"name": "__conversational_response", inputs: {"response": "The amount of transaction ..."} }
            
            Respond only with a JSON object containing required fields:
                - "name": <required selected tool name>
                - "inputs": <required selected tool properties, matching the tool's JSON schema.
                    Do not use tool definition in inputs. Ex: { "arg0": 5} >
            
            If the user request does not imply a response, respond with what have been done.
            """);

    /**
     * Default response tool specification
     */
    ToolSpecification DEFAULT_RESPONSE_TOOL = ToolSpecification.builder()
            .name("__conversational_response")
            .description("Respond conversationally if no other tools should be called for a given query.")
            .parameters(ToolParameters.builder()
                    .type("object")
                    .properties(
                            Map.of("response",
                                    Map.of("type", "string",
                                            "description", "Conversational response to the user.")))
                    .required(Collections.singletonList("response"))
                    .build())
            .build();

    record ToolResponse(String name, Map<String, Object> inputs, String result_id) {
    }

    private final OllamaClient client;
    private final String modelName;
    private final Options options;

    ExperimentalSequentialToolsDelegate(OllamaClient client, String modelName, Options options) {
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
        List<Message> ollamaMessages = ExperimentalMessagesUtils.toOllamaGroupedMessages(messages);
        Message systemMessage = createSystemMessageWithTools(ollamaMessages, toolSpecifications);

        List<Message> otherMessages = ollamaMessages.stream().filter(om -> om.getRole() != Role.SYSTEM).toList();
        List<Message> optimizedMessages = new ArrayList<>(otherMessages.size()+1);
        optimizedMessages.add(systemMessage);
        optimizedMessages.addAll(otherMessages);

        builder.messages(optimizedMessages);

        ChatResponse response = client.chat(builder.build());
        ToolResponse toolResponse;
        try {
            toolResponse = Json.fromJson(response.getMessage().getContent(), ToolResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Ollama server did not respond with valid JSON. Please try again!");
        }

        if (toolResponse.name.equals(DEFAULT_RESPONSE_TOOL.name())) {
            return Response.from(AiMessage.from(toolResponse.inputs.get("response").toString()),
                    new TokenUsage(response.getPromptEvalCount(), response.getEvalCount()), FinishReason.STOP);
        }

        List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();
        List<String> availableTools = toolSpecifications.stream().map(ToolSpecification::name).toList();

        if (!availableTools.contains(toolResponse.name)) {
            throw new RuntimeException(String.format(
                    "Ollama server wants to call a name '%s' that is not part of the available tools %s",
                    toolResponse.name, availableTools));
        } else {
            getToolSpecification(toolResponse, toolSpecifications)
                    .map(ts -> toToolExecutionRequest(toolResponse, ts))
                    .ifPresent(toolExecutionRequests::add);
        }

        return Response.from(AiMessage.from(toolExecutionRequests));
    }

    private Message createSystemMessageWithTools(List<Message> messages, List<ToolSpecification> toolSpecifications) {
        String initialSystemMessages = messages.stream().filter(sm -> sm.getRole() == Role.SYSTEM)
                .map(Message::getContent)
                .collect(Collectors.joining("\n"));
        String history = messages.stream().filter(sm -> sm.getRole() == Role.USER)
                .skip(1)
                .map(Message::getContent)
                .collect(Collectors.joining("\n"));
        List<ToolSpecification> tools = new ArrayList<>(toolSpecifications);
        tools.add(DEFAULT_RESPONSE_TOOL);
        Prompt prompt = DEFAULT_SYSTEM_TEMPLATE.apply(
                Map.of("tools", convertToOllamaTool(tools),
                        "context", initialSystemMessages,
                        "history", history)
        );
        return Message.builder()
                .role(Role.SYSTEM)
                .content(prompt.text())
                .build();
    }

    private record OllamaTool(String name, String description, Map<String, Map<String, Object>> properties,
                              List<String> required) {}

    private String convertToOllamaTool(List<ToolSpecification> tools) {
        List<OllamaTool> ollamaTools = tools.stream()
                .map(toolSpecification -> new OllamaTool(toolSpecification.name(),
                        toolSpecification.description(), toolSpecification.parameters().properties(),
                        toolSpecification.parameters().required())
                ).toList();
        return Json.toJson(ollamaTools);
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
}
