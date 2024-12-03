package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.output.JsonSchemas;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.service.ToolExecutionStrategy.EXECUTE_TOOL;
import static dev.langchain4j.service.ToolExecutionStrategy.RETURN_TOOL_EXECUTION_REQUEST;
import static java.util.Arrays.asList;

public class ChatService {
    // TODO returnString() return(Person.class), returnListOf(Person.class)

    private final ChatLanguageModel chatLanguageModel;
    private final String systemMessage;
    private final String userMessage;
    private final Double temperature;
    private final ChatMemory chatMemory;
    private final RetrievalAugmentor retrievalAugmentor;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ToolExecutionStrategy toolCallStrategy;
    // TODO default model params
    // TODO try this with Quarkus and SB

    // TODO default chat memory when String return type?

    // TODO set model parameters dynamically
    // TODO pass tool params dynamically

    // TODO aroundToolCall(...) ? onToolCall()?

    private ChatService(Builder builder) {
        this.chatLanguageModel = builder.chatLanguageModel;
        this.systemMessage = builder.systemMessage;
        this.userMessage = builder.userMessage;
        this.temperature = builder.temperature;
        this.chatMemory = builder.chatMemory;
        this.retrievalAugmentor = builder.retrievalAugmentor;
        this.toolSpecifications = builder.toolSpecifications; // TODO copy?
        this.toolExecutors = builder.toolExecutors; // TODO copy?
        this.toolCallStrategy = builder.toolExecutionStrategy; // TODO copy?
    }

    public static ChatService createFrom(ChatLanguageModel chatModel) { // TODO name
        return ChatService.builder().chatModel(chatModel).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ChatLanguageModel chatLanguageModel;
        private String systemMessage;
        private String userMessage;
        private Double temperature;
        private ChatMemory chatMemory;
        private RetrievalAugmentor retrievalAugmentor;
        private List<ToolSpecification> toolSpecifications = new ArrayList<>();
        private Map<String, ToolExecutor> toolExecutors = new HashMap<>();
        private ToolExecutionStrategy toolExecutionStrategy = EXECUTE_TOOL;

        private Builder() {
        }

        public Builder chatModel(ChatLanguageModel chatModel) {
            this.chatLanguageModel = chatModel;
            return this;
        }

        public Builder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public Builder userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder tools(Object... objectsWithTools) {
            return tools(asList(objectsWithTools));
        }

        public Builder tools(List<Object> objectsWithTools) { // TODO Collection?
            // TODO validate uniqueness of tool names

            for (Object objectWithTool : objectsWithTools) {
                if (objectWithTool instanceof Class) {
                    throw illegalConfiguration("Tool '%s' must be an object, not a class", objectWithTool);
                }

                for (Method method : objectWithTool.getClass().getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Tool.class)) {
                        ToolSpecification toolSpecification = toolSpecificationFrom(method);
                        toolSpecifications.add(toolSpecification);
                        toolExecutors.put(toolSpecification.name(), new DefaultToolExecutor(objectWithTool, method));
                    }
                }
            }

            return this;
        }

        public Builder onToolCall(ToolExecutionStrategy toolExecutionStrategy) { // TODO make it configurable on the request builder
            this.toolExecutionStrategy = toolExecutionStrategy;
            return this;
        }

        public ChatService build() {
            return new ChatService(this);
        }
    }

    public RequestBuilder buildRequest() { // TODO name. Required at all?
        return new RequestBuilder();
    }

    public class RequestBuilder {

        private String systemMessage;
        private String userMessage;
        private Map<String, Object> promptTemplateVariables = new HashMap<>();
        private Double temperature;
        private Set<String> toolNames;

        public RequestBuilder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public RequestBuilder userMessage(String userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public RequestBuilder variable(String name, String value) {
            // TODO allow null/empty/blank values?
            promptTemplateVariables.put(ensureNotBlank(name, "name"), ensureNotNull(value, "value"));
            return this;
        }

        public RequestBuilder variables(Map<String, Object> variables) {
            promptTemplateVariables = variables; // TODO copy? validate?
            return this;
        }

        public RequestBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public RequestBuilder toolNames(String... toolNames) {
            this.toolNames = new LinkedHashSet<>(asList(toolNames)); // TODO
            return this;
        }

        // TODO tools/toolNames
        // TODO etc
        // TODO memory id

        public String outputString() {
            ChatResponse chatResponse = chat(
                    systemMessage,
                    userMessage,
                    promptTemplateVariables,
                    temperature,
                    toolNames,
                    String.class
            );
            return chatResponse.aiMessage().text();
        }

        public ChatResponse outputResponse() {
            return chat(
                    systemMessage,
                    userMessage,
                    promptTemplateVariables,
                    temperature,
                    toolNames,
                    String.class
            );
        }

        public <T> T output(Class<T> type) {
            ChatResponse chatResponse = chat(
                    systemMessage,
                    userMessage,
                    promptTemplateVariables,
                    temperature,
                    toolNames,
                    type
            );
            return (T) new ServiceOutputParser().parse(Response.from(chatResponse.aiMessage()), type); // TODO
        }

        public <T> List<T> outputListOf(Class<T> type) {
            throw new RuntimeException("not implemented");
        }
    }

    String chat(String userMessage) {
        ChatResponse chatResponse = chat(
                systemMessage,
                userMessage,
                Map.of(),
                temperature,
                Set.of(),
                String.class
        );
        return chatResponse.aiMessage().text();
    }

    // TODO chat(ChatRequest)? should this ignore memory, tools, etc?

    private ChatResponse chat(String systemMessageString,
                              String userMessageString,
                              Map<String, Object> promptTemplateVariables,
                              Double temperature,
                              Set<String> toolNames,
                              Class<?> outputType) { // TODO multimodality

        List<ChatMessage> messages = new ArrayList<>();

        systemMessageString = getOrDefault(systemMessageString, this.systemMessage); // TODO test
        userMessageString = getOrDefault(userMessageString, this.userMessage); // TODO test

        if (!isNullOrBlank(systemMessageString)) {
            SystemMessage systemMessage = PromptTemplate.from(systemMessageString)
                    .apply(promptTemplateVariables)
                    .toSystemMessage();
            messages.add(systemMessage);
            if (chatMemory != null) {
                chatMemory.add(systemMessage);
            }
        }

        if (!isNullOrBlank(userMessageString)) {
            UserMessage userMessage = PromptTemplate.from(userMessageString)
                    .apply(promptTemplateVariables)
                    .toUserMessage();
            messages.add(userMessage);
            if (chatMemory != null) {
                chatMemory.add(userMessage);
            }
        }

        if (chatMemory != null) {
            // TODO ephemeral chat memory
            messages = chatMemory.messages();
        }

        ResponseFormat responseFormat = null;
        if (outputType != String.class) {
            responseFormat = ResponseFormat.builder()
                    .type(ResponseFormatType.JSON)
                    .jsonSchema(JsonSchemas.jsonSchemaFrom(outputType).get())
                    .build();
        }

        List<ToolSpecification> toolSpecifications = this.toolSpecifications;
        if (!isNullOrEmpty(toolNames)) {
            toolSpecifications = toolSpecifications.stream()
                    .filter(toolSpecification -> toolNames.contains(toolSpecification.name()))
                    .toList();
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .responseFormat(responseFormat)
                .temperature(getOrDefault(temperature, this.temperature))
                .build();

        ChatResponse chatResponse = chatLanguageModel.chat(chatRequest);
        AiMessage aiMessage = chatResponse.aiMessage();

        if (chatMemory != null) {
            chatMemory.add(aiMessage);
        } else {
            messages.add(aiMessage);
        }

        while (true) { // TODO limit

            if (aiMessage.hasToolExecutionRequests()) {

                String result = null; // TODO?

                for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                    ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());
                    String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, null); // TODO
                    result = toolExecutionResult;
                    ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                            toolExecutionRequest,
                            toolExecutionResult
                    );
                    if (chatMemory != null) {
                        chatMemory.add(toolExecutionResultMessage);
                    } else {
                        messages.add(toolExecutionResultMessage);
                    }
                }

                if (toolCallStrategy == RETURN_TOOL_EXECUTION_REQUEST) { // TODO what if multiple tools?
                    return chatResponse;
                }

                chatRequest = ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(toolSpecifications)
                        .build();

                chatResponse = chatLanguageModel.chat(chatRequest);
                aiMessage = chatResponse.aiMessage();

            } else {
                return chatResponse;
            }
        }
    }

    // TODO return fluent builder
}
