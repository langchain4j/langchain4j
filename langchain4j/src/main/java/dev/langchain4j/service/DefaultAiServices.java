package dev.langchain4j.service;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.TypeUtils.typeHasRawClass;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.guardrail.GuardrailService;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.service.tool.ToolServiceResult;
import dev.langchain4j.spi.services.TokenStreamAdapter;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Internal
class DefaultAiServices<T> extends AiServices<T> {

    private final ServiceOutputParser serviceOutputParser = new ServiceOutputParser();
    private final Collection<TokenStreamAdapter> tokenStreamAdapters = loadFactories(TokenStreamAdapter.class);

    DefaultAiServices(AiServiceContext context) {
        super(context);
    }

    static void validateParameters(Class<?> aiServiceClass, Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters == null || parameters.length < 2) {
            return;
        }

        boolean invocationParametersExist = false;

        for (Parameter parameter : parameters) {
            V v = parameter.getAnnotation(V.class);
            dev.langchain4j.service.UserMessage userMessage =
                    parameter.getAnnotation(dev.langchain4j.service.UserMessage.class);
            MemoryId memoryId = parameter.getAnnotation(MemoryId.class);
            UserName userName = parameter.getAnnotation(UserName.class);

            if (InvocationParameters.class.isAssignableFrom(parameter.getType())) {
                if (invocationParametersExist) {
                    throw illegalConfiguration("There can be at most one parameter of type %s",
                            InvocationParameters.class.getName());
                }
                invocationParametersExist = true;
                continue;
            }

            if (userMessage == null && v == null && memoryId == null && userName == null) {
                throw illegalConfiguration(
                        "The parameter '%s' in the method '%s' of the class %s must be annotated with either " +
                                "%s, %s, %s, or %s, or it should be of type %s",
                        parameter.getName(), method.getName(), aiServiceClass.getName(),
                        dev.langchain4j.service.UserMessage.class.getName(),
                        V.class.getName(),
                        MemoryId.class.getName(),
                        UserName.class.getName(),
                        InvocationParameters.class.getName()
                );
            }
        }
    }

    private void validateContextMemory() {
        if (!context.hasChatMemory() && ChatMemoryAccess.class.isAssignableFrom(context.aiServiceClass)) {
            throw illegalConfiguration(
                    "In order to have a service implementing ChatMemoryAccess, please configure the ChatMemoryProvider on the '%s'.",
                    context.aiServiceClass.getName());
        }
    }

    private void validateMethods() {

        for (Method method : context.aiServiceClass.getMethods()) {
            if (method.isAnnotationPresent(Moderate.class) && context.moderationModel == null) {
                throw illegalConfiguration(
                        "The @Moderate annotation is present, but the moderationModel is not set up. "
                                + "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
            }

            Class<?> returnType = method.getReturnType();
            if (returnType == void.class) {
                throw illegalConfiguration(
                        "'%s' is not a supported return type of an AI Service method", returnType.getName());
            }
            if (returnType == Result.class || returnType == List.class || returnType == Set.class) {
                TypeUtils.validateReturnTypesAreProperlyParametrized(method.getName(), method.getGenericReturnType());
            }

            if (!context.hasChatMemory()) {
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(MemoryId.class)) {
                        throw illegalConfiguration(
                                "In order to use @MemoryId, please configure the ChatMemoryProvider on the '%s'.",
                                context.aiServiceClass.getName());
                    }
                }
            }
        }
    }

    protected void validate() {
        performBasicValidation();
        validateContextMemory();
        validateMethods();
    }

    private Object handleChatMemoryAccess(Method method, Object[] args) {
        return switch (method.getName()) {
            case "getChatMemory" -> context.chatMemoryService.getChatMemory(args[0]);
            case "evictChatMemory" -> context.chatMemoryService.evictChatMemory(args[0]) != null;
            default ->
                throw new UnsupportedOperationException(
                        "Unknown method on ChatMemoryAccess class : " + method.getName());
        };
    }

    public T build() {
        validate();

        Object proxyInstance = Proxy.newProxyInstance(
                context.aiServiceClass.getClassLoader(),
                new Class<?>[] {context.aiServiceClass},
                new InvocationHandler() {

                    private final ExecutorService executor = Executors.newCachedThreadPool();

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }

                        if (method.getDeclaringClass() == Object.class) {
                            switch (method.getName()) {
                                case "equals":
                                    return proxy == args[0];
                                case "hashCode":
                                    return System.identityHashCode(proxy);
                                case "toString":
                                    return context.aiServiceClass.getName() + "@" +
                                            Integer.toHexString(System.identityHashCode(proxy));
                                default:
                                    throw new IllegalStateException("Unexpected Object method: " + method);
                            }
                        }

                        if (method.getDeclaringClass() == ChatMemoryAccess.class) {
                            return handleChatMemoryAccess(method, args);
                        }

                        // TODO do it once, when creating AI Service?
                        validateParameters(context.aiServiceClass, method);

                        Object memoryId = findMemoryId(method, args).orElse(ChatMemoryService.DEFAULT);
                        ChatMemory chatMemory = context.hasChatMemory()
                                ? context.chatMemoryService.getOrCreateChatMemory(memoryId)
                                : null;

                        Optional<SystemMessage> systemMessage = prepareSystemMessage(memoryId, method, args);
                        var userMessageTemplate = getUserMessageTemplate(method, args);
                        var variables = InternalReflectionVariableResolver.findTemplateVariables(
                                userMessageTemplate, method, args);
                        UserMessage userMessage = prepareUserMessage(method, args, userMessageTemplate, variables);

                        InvocationParameters invocationParameters = findInvocationParameters(args, method.getParameters())
                                .orElseGet(InvocationParameters::new);
                        InvocationContext invocationContext = InvocationContext.builder()
                                .chatMemoryId(memoryId)
                                .invocationParameters(invocationParameters)
                                .build();

                        AugmentationResult augmentationResult = null;
                        if (context.retrievalAugmentor != null) {
                            List<ChatMessage> chatMemoryMessages = chatMemory != null ? chatMemory.messages() : null;
                            Metadata metadata = Metadata.builder()
                                    .chatMessage(userMessage)
                                    .chatMemory(chatMemoryMessages)
                                    .invocationContext(invocationContext)
                                    .build();
                            AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);
                            augmentationResult = context.retrievalAugmentor.augment(augmentationRequest);
                            userMessage = (UserMessage) augmentationResult.chatMessage();
                        }

                        var commonGuardrailParam = GuardrailRequestParams.builder()
                                .chatMemory(chatMemory)
                                .augmentationResult(augmentationResult)
                                .userMessageTemplate(userMessageTemplate)
                                .variables(variables)
                                .build();

                        userMessage = invokeInputGuardrails(
                                context.guardrailService(), method, userMessage, commonGuardrailParam);

                        Type returnType = method.getGenericReturnType();
                        boolean streaming = returnType == TokenStream.class || canAdaptTokenStreamTo(returnType);

                        // TODO should it be called when returnType==String?
                        boolean supportsJsonSchema = supportsJsonSchema();

                        Optional<JsonSchema> jsonSchema = Optional.empty();
                        if (supportsJsonSchema && !streaming) {
                            jsonSchema = serviceOutputParser.jsonSchema(returnType);
                        }
                        if ((!supportsJsonSchema || jsonSchema.isEmpty()) && !streaming) {
                            userMessage = appendOutputFormatInstructions(returnType, userMessage);
                        }

                        Optional<List<Content>> maybeContents = findContents(method, args);
                        if (maybeContents.isPresent()) {
                            List<Content> allContents = new ArrayList<>();
                            for (Content content : maybeContents.get()) {
                                if (content == null) { // placeholder
                                    allContents.addAll(userMessage.contents());
                                } else {
                                    allContents.add(content);
                                }
                            }
                            userMessage = UserMessage.from(userMessage.name(), allContents);
                        }

                        List<ChatMessage> messages = new ArrayList<>();
                        if (context.hasChatMemory()) {
                            systemMessage.ifPresent(chatMemory::add);
                            chatMemory.add(userMessage);
                            messages.addAll(chatMemory.messages());
                        } else {
                            systemMessage.ifPresent(messages::add);
                            messages.add(userMessage);
                        }

                        Future<Moderation> moderationFuture = triggerModerationIfNeeded(method, messages);

                        ToolServiceContext toolServiceContext =
                                context.toolService.createContext(invocationContext, userMessage);

                        if (streaming) {
                            var tokenStreamParameters = AiServiceTokenStreamParameters.builder()
                                    .messages(messages)
                                    .toolSpecifications(toolServiceContext.toolSpecifications())
                                    .toolExecutors(toolServiceContext.toolExecutors())
                                    .toolArgumentsErrorHandler(context.toolService.argumentsErrorHandler())
                                    .toolExecutionErrorHandler(context.toolService.executionErrorHandler())
                                    .toolExecutor(context.toolService.executor())
                                    .retrievedContents(
                                            augmentationResult != null ? augmentationResult.contents() : null)
                                    .context(context)
                                    .invocationContext(invocationContext)
                                    .commonGuardrailParams(commonGuardrailParam)
                                    .methodKey(method)
                                    .build();

                            TokenStream tokenStream = new AiServiceTokenStream(tokenStreamParameters);
                            // TODO moderation
                            if (returnType == TokenStream.class) {
                                return tokenStream;
                            } else {
                                return adapt(tokenStream, returnType);
                            }
                        }

                        ResponseFormat responseFormat = null;
                        if (supportsJsonSchema && jsonSchema.isPresent()) {
                            responseFormat = ResponseFormat.builder()
                                    .type(JSON)
                                    .jsonSchema(jsonSchema.get())
                                    .build();
                        }

                        ChatRequestParameters parameters = ChatRequestParameters.builder()
                                .toolSpecifications(toolServiceContext.toolSpecifications())
                                .responseFormat(responseFormat)
                                .build();

                        ChatRequest chatRequest = context.chatRequestTransformer
                                .apply(ChatRequest.builder()
                                        .messages(messages)
                                        .parameters(parameters)
                                        .build(), memoryId);

                        ChatExecutor chatExecutor = ChatExecutor.builder(context.chatModel)
                                .chatRequest(chatRequest)
                                .build();

                        ChatResponse chatResponse = chatExecutor.execute();

                        verifyModerationIfNeeded(moderationFuture);

                        boolean isReturnTypeResult = typeHasRawClass(returnType, Result.class);

                        ToolServiceResult toolServiceResult = context.toolService.executeInferenceAndToolsLoop(
                                chatResponse,
                                parameters,
                                messages,
                                context.chatModel,
                                chatMemory,
                                invocationContext,
                                toolServiceContext.toolExecutors(),
                                isReturnTypeResult);

                        if (toolServiceResult.immediateToolReturn() && isReturnTypeResult) {
                            return Result.builder()
                                    .content(null)
                                    .tokenUsage(toolServiceResult.aggregateTokenUsage())
                                    .sources(augmentationResult == null ? null : augmentationResult.contents())
                                    .finishReason(TOOL_EXECUTION)
                                    .toolExecutions(toolServiceResult.toolExecutions())
                                    .intermediateResponses(toolServiceResult.intermediateResponses())
                                    .finalResponse(toolServiceResult.finalResponse())
                                    .build();
                        }

                        ChatResponse aggregateResponse = toolServiceResult.aggregateResponse();

                        var response = invokeOutputGuardrails(
                                context.guardrailService(), method, aggregateResponse, chatExecutor, commonGuardrailParam);

                        if ((response != null) && typeHasRawClass(returnType, response.getClass())) {
                            return response;
                        }

                        var parsedResponse = serviceOutputParser.parse((ChatResponse) response, returnType);

                        if (isReturnTypeResult) {
                            return Result.builder()
                                    .content(parsedResponse)
                                    .tokenUsage(toolServiceResult.aggregateTokenUsage())
                                    .sources(augmentationResult == null ? null : augmentationResult.contents())
                                    .finishReason(toolServiceResult.finalResponse().finishReason())
                                    .toolExecutions(toolServiceResult.toolExecutions())
                                    .intermediateResponses(toolServiceResult.intermediateResponses())
                                    .finalResponse(toolServiceResult.finalResponse())
                                    .build();
                        } else {
                            return parsedResponse;
                        }
                    }

                    private Optional<InvocationParameters> findInvocationParameters(Object[] arguments, Parameter[] parameters) {
                        if (arguments == null) {
                            return Optional.empty();
                        }
                        for (int i = 0; i < parameters.length; i++) {
                            Parameter parameter = parameters[i];
                            if (InvocationParameters.class.isAssignableFrom(parameter.getType())) {
                                InvocationParameters invocationParameters = (InvocationParameters) arguments[i];
                                ensureNotNull(invocationParameters, "InvocationParameters");
                                return Optional.of(invocationParameters);
                            }
                        }
                        return Optional.empty();
                    }

                    private boolean canAdaptTokenStreamTo(Type returnType) {
                        for (TokenStreamAdapter tokenStreamAdapter : tokenStreamAdapters) {
                            if (tokenStreamAdapter.canAdaptTokenStreamTo(returnType)) {
                                return true;
                            }
                        }
                        return false;
                    }

                    private Object adapt(TokenStream tokenStream, Type returnType) {
                        for (TokenStreamAdapter tokenStreamAdapter : tokenStreamAdapters) {
                            if (tokenStreamAdapter.canAdaptTokenStreamTo(returnType)) {
                                return tokenStreamAdapter.adapt(tokenStream);
                            }
                        }
                        throw new IllegalStateException("Can't find suitable TokenStreamAdapter");
                    }

                    private boolean supportsJsonSchema() {
                        return context.chatModel != null
                                && context.chatModel.supportedCapabilities().contains(RESPONSE_FORMAT_JSON_SCHEMA);
                    }

                    private UserMessage appendOutputFormatInstructions(Type returnType, UserMessage userMessage) {
                        String outputFormatInstructions = serviceOutputParser.outputFormatInstructions(returnType);
                        String text = userMessage.singleText() + outputFormatInstructions;
                        if (isNotNullOrBlank(userMessage.name())) {
                            userMessage = UserMessage.from(userMessage.name(), text);
                        } else {
                            userMessage = UserMessage.from(text);
                        }
                        return userMessage;
                    }

                    private Future<Moderation> triggerModerationIfNeeded(Method method, List<ChatMessage> messages) {
                        if (method.isAnnotationPresent(Moderate.class)) {
                            return executor.submit(() -> {
                                List<ChatMessage> messagesToModerate = removeToolMessages(messages);
                                return context.moderationModel
                                        .moderate(messagesToModerate)
                                        .content();
                            });
                        }
                        return null;
                    }
                });

        return (T) proxyInstance;
    }

    private UserMessage invokeInputGuardrails(
            GuardrailService guardrailService,
            Method method,
            UserMessage userMessage,
            GuardrailRequestParams commonGuardrailParams) {

        // NOTE: This check is cached, so it really only needs to be computed the first time for each method
        if (guardrailService.hasInputGuardrails(method)) {
            var inputGuardrailRequest = InputGuardrailRequest.builder()
                    .userMessage(userMessage)
                    .commonParams(commonGuardrailParams)
                    .build();
            return guardrailService.executeGuardrails(method, inputGuardrailRequest);
        }

        return userMessage;
    }

    private <T> T invokeOutputGuardrails(
            GuardrailService guardrailService,
            Method method,
            ChatResponse responseFromLLM,
            ChatExecutor chatExecutor,
            GuardrailRequestParams commonGuardrailParams) {

        if (guardrailService.hasOutputGuardrails(method)) {
            var outputGuardrailRequest = OutputGuardrailRequest.builder()
                    .responseFromLLM(responseFromLLM)
                    .chatExecutor(chatExecutor)
                    .requestParams(commonGuardrailParams)
                    .build();
            return guardrailService.executeGuardrails(method, outputGuardrailRequest);
        }

        return (T) responseFromLLM;
    }

    private Optional<SystemMessage> prepareSystemMessage(Object memoryId, Method method, Object[] args) {
        return findSystemMessageTemplate(memoryId, method).map(systemMessageTemplate -> PromptTemplate.from(
                        systemMessageTemplate)
                .apply(InternalReflectionVariableResolver.findTemplateVariables(systemMessageTemplate, method, args))
                .toSystemMessage());
    }

    private Optional<String> findSystemMessageTemplate(Object memoryId, Method method) {
        dev.langchain4j.service.SystemMessage annotation =
                method.getAnnotation(dev.langchain4j.service.SystemMessage.class);
        if (annotation != null) {
            return Optional.of(getTemplate(
                    method, "System", annotation.fromResource(), annotation.value(), annotation.delimiter()));
        }

        return context.systemMessageProvider.apply(memoryId);
    }

    private static UserMessage prepareUserMessage(
            Method method, Object[] args, String userMessageTemplate, Map<String, Object> variables) {
        Prompt prompt = PromptTemplate.from(userMessageTemplate).apply(variables);

        Optional<String> maybeUserName = findUserName(method.getParameters(), args);
        return maybeUserName
                .map(userName -> UserMessage.from(userName, prompt.text()))
                .orElseGet(prompt::toUserMessage);
    }

    private static String getUserMessageTemplate(Method method, Object[] args) {

        Optional<String> templateFromMethodAnnotation = findUserMessageTemplateFromMethodAnnotation(method);
        Optional<String> templateFromParameterAnnotation =
                findUserMessageTemplateFromAnnotatedParameter(method.getParameters(), args);

        if (templateFromMethodAnnotation.isPresent() && templateFromParameterAnnotation.isPresent()) {
            throw illegalConfiguration(
                    "Error: The method '%s' has multiple @UserMessage annotations. Please use only one.",
                    method.getName());
        }

        if (templateFromMethodAnnotation.isPresent()) {
            return templateFromMethodAnnotation.get();
        }
        if (templateFromParameterAnnotation.isPresent()) {
            return templateFromParameterAnnotation.get();
        }

        Optional<String> templateFromTheOnlyArgument =
                findUserMessageTemplateFromTheOnlyArgument(method.getParameters(), args);
        if (templateFromTheOnlyArgument.isPresent()) {
            return templateFromTheOnlyArgument.get();
        }

        throw illegalConfiguration("Error: The method '%s' does not have a user message defined.", method.getName());
    }

    private static Optional<String> findUserMessageTemplateFromMethodAnnotation(Method method) {
        return Optional.ofNullable(method.getAnnotation(dev.langchain4j.service.UserMessage.class))
                .map(a -> getTemplate(method, "User", a.fromResource(), a.value(), a.delimiter()));
    }

    private static Optional<String> findUserMessageTemplateFromAnnotatedParameter(
            Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(dev.langchain4j.service.UserMessage.class)
                    && !(args[i] instanceof Content) && !isListOfContents(args[i])) {
                return Optional.of(InternalReflectionVariableResolver.asString(args[i]));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findUserMessageTemplateFromTheOnlyArgument(Parameter[] parameters, Object[] args) {
        if (parameters != null && parameters.length == 1 && parameters[0].getAnnotations().length == 0) {
            return Optional.of(InternalReflectionVariableResolver.asString(args[0]));
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

    private static Optional<List<Content>> findContents(Method method, Object[] args) {
        List<Content> contents = new ArrayList<>();

        if (findUserMessageTemplateFromMethodAnnotation(method).isPresent()) {
            contents.add(null); // placeholder
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(dev.langchain4j.service.UserMessage.class)) {
                if (args[i] instanceof Content) {
                    contents.add((Content) args[i]);
                } else if (isListOfContents(args[i])) {
                    contents.addAll((List<Content>) args[i]);
                } else {
                    contents.add(null); // placeholder
                }
            }
        }

        if (contents.stream().filter(Objects::isNull).count() > 1) {
            throw illegalConfiguration(
                    "Error: The method '%s' has multiple @UserMessage for text content. Please use only one.",
                    method.getName());
        }

        return contents.isEmpty() ? Optional.empty() : Optional.of(contents);
    }

    private static boolean isListOfContents(Object o) {
        return o instanceof List<?> list && list.stream().allMatch(Content.class::isInstance);
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

    private static String getResourceText(Class<?> clazz, String resource) {
        InputStream inputStream = clazz.getResourceAsStream(resource);
        if (inputStream == null) {
            inputStream = clazz.getResourceAsStream("/" + resource);
        }
        return getText(inputStream);
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
                            parameters[i].getName(), method.getName());
                }
                return Optional.of(memoryId);
            }
        }
        return Optional.empty();
    }
}
