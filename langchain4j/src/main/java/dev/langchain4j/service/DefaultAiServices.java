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

import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.service.ServiceOutputParser.outputFormatInstructions;
import static dev.langchain4j.service.ServiceOutputParser.parse;

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
                        "Parameter '%s' of method '%s' should be annotated with @V or @UserMessage " +
                                "or @UserName or @MemoryId", parameter.getName(), method.getName()
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

                        Object memoryId = findMemoryId(method, args).orElse(DEFAULT);

                        Optional<SystemMessage> systemMessage = prepareSystemMessage(memoryId, method, args);
                        UserMessage userMessage = prepareUserMessage(method, args);

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
                            } else {
                                messages = new ArrayList<>(messages);
                                messages.add(aiMessage);
                            }

                            if (!aiMessage.hasToolExecutionRequests()) {
                                break;
                            }

                            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                                ToolExecutor toolExecutor = context.toolExecutors.get(toolExecutionRequest.name());
                                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                                        toolExecutionRequest,
                                        toolExecutionResult
                                );
                                if (context.hasChatMemory()) {
                                    context.chatMemory(memoryId).add(toolExecutionResultMessage);
                                } else {
                                    messages.add(toolExecutionResultMessage);
                                }
                            }

                            if (context.hasChatMemory()) {
                                messages = context.chatMemory(memoryId).messages();
                            }

                            response = context.chatModel.generate(messages, context.toolSpecifications);
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

    private Optional<SystemMessage> prepareSystemMessage(Object memoryId, Method method, Object[] args) {
        return findSystemMessageTemplate(memoryId, method)
                .map(systemMessageTemplate -> PromptTemplate.from(systemMessageTemplate)
                        .apply(findTemplateVariables(systemMessageTemplate, method, args))
                        .toSystemMessage());
    }

    private Optional<String> findSystemMessageTemplate(Object memoryId, Method method) {
        dev.langchain4j.service.SystemMessage annotation = method.getAnnotation(dev.langchain4j.service.SystemMessage.class);
        if (annotation != null) {
            return Optional.of(getTemplate(method, "System", annotation.fromResource(), annotation.value(), annotation.delimiter()));
        }

        return context.systemMessageProvider.apply(memoryId);
    }

    private static Map<String, Object> findTemplateVariables(String template, Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();

        Map<String, Object> variables = new HashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            V annotation = parameters[i].getAnnotation(V.class);
            if (annotation != null) {
                String variableName = annotation.value();
                Object variableValue = args[i];
                variables.put(variableName, variableValue);
            }
        }

        if (template.contains("{{it}}") && !variables.containsKey("it")) {
            String itValue = getValueOfVariableIt(parameters, args);
            variables.put("it", itValue);
        }

        return variables;
    }

    private static String getValueOfVariableIt(Parameter[] parameters, Object[] args) {
        if (parameters.length == 1) {
            Parameter parameter = parameters[0];
            if (!parameter.isAnnotationPresent(dev.langchain4j.service.MemoryId.class)
                    && !parameter.isAnnotationPresent(dev.langchain4j.service.UserMessage.class)
                    && !parameter.isAnnotationPresent(dev.langchain4j.service.UserName.class)
                    && (!parameter.isAnnotationPresent(dev.langchain4j.service.V.class) || isAnnotatedWithIt(parameter))) {
                return toString(args[0]);
            }
        }

        for (int i = 0; i < parameters.length; i++) {
            if (isAnnotatedWithIt(parameters[i])) {
                return toString(args[i]);
            }
        }

        throw illegalConfiguration("Error: cannot find the value of the prompt template variable \"{{it}}\".");
    }

    private static boolean isAnnotatedWithIt(Parameter parameter) {
        V annotation = parameter.getAnnotation(V.class);
        return annotation != null && "it".equals(annotation.value());
    }

    private static UserMessage prepareUserMessage(Method method, Object[] args) {

        String template = getUserMessageTemplate(method, args);
        Map<String, Object> variables = findTemplateVariables(template, method, args);

        Prompt prompt = PromptTemplate.from(template).apply(variables);

        Optional<String> maybeUserName = findUserName(method.getParameters(), args);
        return maybeUserName.map(userName -> UserMessage.from(userName, prompt.text()))
                .orElseGet(prompt::toUserMessage);
    }

    private static String getUserMessageTemplate(Method method, Object[] args) {

        Optional<String> templateFromMethodAnnotation = findUserMessageTemplateFromMethodAnnotation(method);
        Optional<String> templateFromParameterAnnotation = findUserMessageTemplateFromAnnotatedParameter(method.getParameters(), args);

        if (templateFromMethodAnnotation.isPresent() && templateFromParameterAnnotation.isPresent()) {
            throw illegalConfiguration(
                    "Error: The method '%s' has multiple @UserMessage annotations. Please use only one.",
                    method.getName()
            );
        }

        if (templateFromMethodAnnotation.isPresent()) {
            return templateFromMethodAnnotation.get();
        }
        if (templateFromParameterAnnotation.isPresent()) {
            return templateFromParameterAnnotation.get();
        }

        Optional<String> templateFromTheOnlyArgument = findUserMessageTemplateFromTheOnlyArgument(method.getParameters(), args);
        if (templateFromTheOnlyArgument.isPresent()) {
            return templateFromTheOnlyArgument.get();
        }

        throw illegalConfiguration("Error: The method '%s' does not have a user message defined.", method.getName());
    }

    private static Optional<String> findUserMessageTemplateFromMethodAnnotation(Method method) {
        return Optional.ofNullable(method.getAnnotation(dev.langchain4j.service.UserMessage.class))
                .map(a -> getTemplate(method, "User", a.fromResource(), a.value(), a.delimiter()));
    }

    private static Optional<String> findUserMessageTemplateFromAnnotatedParameter(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(dev.langchain4j.service.UserMessage.class)) {
                return Optional.of(toString(args[i]));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findUserMessageTemplateFromTheOnlyArgument(Parameter[] parameters, Object[] args) {
        if (parameters != null && parameters.length == 1 && parameters[0].getAnnotations().length == 0) {
            return Optional.of(toString(args[0]));
        }
        return Optional.empty();
    }

    private static Optional<String> findUserName(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(UserName.class)) {
                return Optional.of(args[i].toString());
            }
        }
        return Optional.empty();
    }

    private static String getTemplate(Method method, String type, String resource, String[] value, String delimiter) {
        String messageTemplate;
        if (!resource.trim().isEmpty()) {
            messageTemplate = getResourceText(method.getDeclaringClass(), resource);
            if (messageTemplate == null) {
                throw illegalConfiguration("@%sMessage's resource '%s' not found", type, resource);
            }
        } else {
            messageTemplate = String.join(delimiter, value);
        }
        if (messageTemplate.trim().isEmpty()) {
            throw illegalConfiguration("@%sMessage's template cannot be empty", type);
        }
        return messageTemplate;
    }

    private static String getResourceText(Class<?> clazz, String name) {
        return getText(clazz.getResourceAsStream(name));
    }

    private static String getText(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        try (Scanner scanner = new Scanner(inputStream);
             Scanner s = scanner.useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static Optional<Object> findMemoryId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(MemoryId.class)) {
                Object memoryId = args[i];
                if (memoryId == null) {
                    throw illegalArgument(
                            "The value of parameter '%s' annotated with @MemoryId in method '%s' must not be null",
                            parameters[i].getName(), method.getName()
                    );
                }
                return Optional.of(memoryId);
            }
        }
        return Optional.empty();
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
