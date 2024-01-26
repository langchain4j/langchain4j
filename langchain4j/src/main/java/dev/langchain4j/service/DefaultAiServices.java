package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.query.Metadata;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.service.ServiceOutputParser.outputFormatInstructions;
import static dev.langchain4j.service.ServiceOutputParser.parse;
import static java.util.Collections.singletonMap;

class DefaultAiServices<T> extends AiServices<T> {

    private static final int MAX_SEQUENTIAL_TOOL_EXECUTIONS = 10;

    DefaultAiServices(AiServiceContext context) {
        super(context);
    }

    static void validateParameters(Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters == null || parameters.length < 2) {
            return;
        }

        for (Parameter parameter : parameters) {
            V v = parameter.getAnnotation(V.class);
            dev.langchain4j.service.UserMessage userMessage = parameter.getAnnotation(dev.langchain4j.service.UserMessage.class);
            MemoryId memoryId = parameter.getAnnotation(MemoryId.class);
            UserName userName = parameter.getAnnotation(UserName.class);
            if (v == null && userMessage == null && memoryId == null && userName == null) {
                throw illegalConfiguration(
                        "Parameter '%s' of method '%s' should be annotated with @V or @UserMessage or @UserName or @MemoryId",
                        parameter.getName(), method.getName()
                );
            }
        }
    }

    public T build() {

        performBasicValidation();

        for (Method method : context.aiServiceClass.getMethods()) {
            if (method.isAnnotationPresent(Moderate.class) && context.moderationModel == null) {
                throw illegalConfiguration("The @Moderate annotation is present, but the moderationModel is not set up. " +
                        "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
            }
        }

        Object proxyInstance = Proxy.newProxyInstance(
                context.aiServiceClass.getClassLoader(),
                new Class<?>[]{context.aiServiceClass},
                new InvocationHandler() {

                    private final ExecutorService executor = Executors.newCachedThreadPool();

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {

                        if (method.getDeclaringClass() == Object.class) {
                            // methods like equals(), hashCode() and toString() should not be handled by this proxy
                            return method.invoke(this, args);
                        }

                        validateParameters(method);

                        Optional<SystemMessage> systemMessage = prepareSystemMessage(method, args);
                        UserMessage userMessage = prepareUserMessage(method, args);

                        Object memoryId = memoryId(method, args).orElse(DEFAULT);

                        if (context.retrievalAugmentor != null) {
                            List<ChatMessage> chatMemory = context.hasChatMemory()
                                    ? context.chatMemory(memoryId).messages()
                                    : null;
                            Metadata metadata = Metadata.from(userMessage, memoryId, chatMemory);
                            userMessage = context.retrievalAugmentor.augment(userMessage, metadata);
                        }

                        // TODO give user ability to provide custom OutputParser
                        String outputFormatInstructions = outputFormatInstructions(method.getReturnType());
                        userMessage = UserMessage.from(userMessage.text() + outputFormatInstructions);

                        if (context.hasChatMemory()) {
                            ChatMemory chatMemory = context.chatMemory(memoryId);
                            systemMessage.ifPresent(chatMemory::add);
                            chatMemory.add(userMessage);
                        }

                        List<ChatMessage> messages;
                        if (context.hasChatMemory()) {
                            messages = context.chatMemory(memoryId).messages();
                        } else {
                            messages = new ArrayList<>();
                            systemMessage.ifPresent(messages::add);
                            messages.add(userMessage);
                        }

                        Future<Moderation> moderationFuture = triggerModerationIfNeeded(method, messages);

                        if (method.getReturnType() == TokenStream.class) {
                            return new AiServiceTokenStream(messages, context, memoryId); // TODO moderation
                        }

                        Response<AiMessage> response = context.toolSpecifications == null
                                ? context.chatModel.generate(messages)
                                : context.chatModel.generate(messages, context.toolSpecifications);
                        TokenUsage tokenUsageAccumulator = response.tokenUsage();

                        verifyModerationIfNeeded(moderationFuture);

                        int executionsLeft = MAX_SEQUENTIAL_TOOL_EXECUTIONS;
                        while (true) {

                            if (executionsLeft-- == 0) {
                                throw runtime("Something is wrong, exceeded %s sequential tool executions",
                                        MAX_SEQUENTIAL_TOOL_EXECUTIONS);
                            }

                            AiMessage aiMessage = response.content();

                            if (context.hasChatMemory()) {
                                context.chatMemory(memoryId).add(aiMessage);
                            }

                            if (!aiMessage.hasToolExecutionRequests()) {
                                break;
                            }

                            ChatMemory chatMemory = context.chatMemory(memoryId);

                            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                                ToolExecutor toolExecutor = context.toolExecutors.get(toolExecutionRequest.name());
                                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                                        toolExecutionRequest,
                                        toolExecutionResult
                                );
                                chatMemory.add(toolExecutionResultMessage);
                            }

                            response = context.chatModel.generate(chatMemory.messages(), context.toolSpecifications);
                            tokenUsageAccumulator = tokenUsageAccumulator.add(response.tokenUsage());
                        }

                        response = Response.from(response.content(), tokenUsageAccumulator, response.finishReason());
                        return parse(response, method.getReturnType());
                    }

                    private Future<Moderation> triggerModerationIfNeeded(Method method, List<ChatMessage> messages) {
                        if (method.isAnnotationPresent(Moderate.class)) {
                            return executor.submit(() -> {
                                List<ChatMessage> messagesToModerate = removeToolMessages(messages);
                                return context.moderationModel.moderate(messagesToModerate).content();
                            });
                        }
                        return null;
                    }
                });

        return (T) proxyInstance;
    }

    private Optional<SystemMessage> prepareSystemMessage(Method method, Object[] args) {

        Parameter[] parameters = method.getParameters();
        Map<String, Object> variables = getPromptTemplateVariables(args, parameters);

        dev.langchain4j.service.SystemMessage annotation = method.getAnnotation(dev.langchain4j.service.SystemMessage.class);
        if (annotation != null) {

            String systemMessageTemplate = String.join(annotation.delimiter(), annotation.value());
            if (systemMessageTemplate.isEmpty()) {
                throw illegalConfiguration("@SystemMessage's template cannot be empty");
            }

            Prompt prompt = PromptTemplate.from(systemMessageTemplate).apply(variables);
            return Optional.of(prompt.toSystemMessage());
        }

        return Optional.empty();
    }

    private static UserMessage prepareUserMessage(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        Map<String, Object> variables = getPromptTemplateVariables(args, parameters);

        String userName = getUserName(parameters, args);

        dev.langchain4j.service.UserMessage annotation = method.getAnnotation(dev.langchain4j.service.UserMessage.class);
        if (annotation != null) {
            String userMessageTemplate = String.join(annotation.delimiter(), annotation.value());

            if (userMessageTemplate.contains("{{it}}")) {
                if (parameters.length != 1) {
                    throw illegalConfiguration("Error: The {{it}} placeholder is present but the method does not have exactly one parameter. " +
                            "Please ensure that methods using the {{it}} placeholder have exactly one parameter.");
                }

                variables = singletonMap("it", toString(args[0]));
            }

            Prompt prompt = PromptTemplate.from(userMessageTemplate).apply(variables);
            if (userName != null) {
                return userMessage(userName, prompt.text());
            } else {
                return prompt.toUserMessage();
            }
        }

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(dev.langchain4j.service.UserMessage.class)) {
                String text = toString(args[i]);
                if (userName != null) {
                    return userMessage(userName, text);
                } else {
                    return userMessage(text);
                }
            }
        }

        if (args == null || args.length == 0) {
            throw illegalConfiguration("Method should have at least one argument");
        }

        if (args.length == 1) {
            String text = toString(args[0]);
            if (userName != null) {
                return userMessage(userName, text);
            } else {
                return userMessage(text);
            }
        }

        throw illegalConfiguration("For methods with multiple parameters, each parameter must be annotated with @V, @UserMessage, @UserName or @MemoryId");
    }

    private Optional<Object> memoryId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(MemoryId.class)) {
                Object memoryId = args[i];
                if (memoryId == null) {
                    throw illegalArgument("The value of parameter %s annotated with @MemoryId in method %s must not be null",
                            parameters[i].getName(), method.getName());
                }
                return Optional.of(memoryId);
            }
        }
        return Optional.empty();
    }


    private static String getUserName(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(UserName.class)) {
                return args[i].toString();
            }
        }
        return null;
    }

    private static Map<String, Object> getPromptTemplateVariables(Object[] args, Parameter[] parameters) {
        Map<String, Object> variables = new HashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            V varAnnotation = parameters[i].getAnnotation(V.class);
            if (varAnnotation != null) {
                String variableName = varAnnotation.value();
                Object variableValue = args[i];
                variables.put(variableName, variableValue);
            }
        }
        return variables;
    }

    private static String toString(Object arg) {
        if (arg.getClass().isArray()) {
            return arrayToString(arg);
        } else if (arg.getClass().isAnnotationPresent(StructuredPrompt.class)) {
            return StructuredPromptProcessor.toPrompt(arg).text();
        } else {
            return arg.toString();
        }
    }

    private static String arrayToString(Object arg) {
        StringBuilder sb = new StringBuilder("[");
        int length = Array.getLength(arg);
        for (int i = 0; i < length; i++) {
            sb.append(toString(Array.get(arg, i)));
            if (i < length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
