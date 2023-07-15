package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Result;
import dev.langchain4j.retriever.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.ServiceOutputParser.outputFormatInstructions;
import static dev.langchain4j.service.ToolSpecifications.toolSpecificationFrom;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class AiServices<T> {

    private final Logger log = LoggerFactory.getLogger(AiServices.class);

    private final Class<T> aiServiceClass;
    private ChatLanguageModel chatLanguageModel;
    private ChatMemory chatMemory;
    private ModerationModel moderationModel;
    private List<ToolSpecification> toolSpecifications;
    private Map<String, ToolExecutor> toolExecutors;
    private Retriever<TextSegment> retriever;

    private AiServices(Class<T> aiServiceClass) {
        this.aiServiceClass = aiServiceClass;
    }

    public static <T> T create(Class<T> aiService, ChatLanguageModel chatLanguageModel) {
        return builder(aiService)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    public static <T> AiServices<T> builder(Class<T> aiService) {
        return new AiServices<>(aiService);
    }

    public AiServices<T> chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    public AiServices<T> chatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        return this;
    }

    public AiServices<T> moderationModel(ModerationModel moderationModel) {
        this.moderationModel = moderationModel;
        return this;
    }

    public AiServices<T> tools(Object... objectsWithTools) {
        toolSpecifications = new ArrayList<>();
        toolExecutors = new HashMap<>();

        for (Object objectWithTool : objectsWithTools) {
            for (Method method : objectWithTool.getClass().getMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification toolSpecification = toolSpecificationFrom(method);
                    toolSpecifications.add(toolSpecification);
                    toolExecutors.put(toolSpecification.name(), new ToolExecutor(objectWithTool, method));
                }
            }
        }

        return this;
    }

    public AiServices<T> retriever(Retriever<TextSegment> retriever) {
        this.retriever = retriever;
        return this;
    }

    public T build() {

        if (chatLanguageModel == null) {
            throw illegalConfiguration("chatLanguageModel is mandatory");
        }

        for (Method method : aiServiceClass.getMethods()) {
            if (method.isAnnotationPresent(Moderate.class) && moderationModel == null) {
                throw illegalConfiguration("The @Moderate annotation is present, but the moderationModel is not set up. " +
                        "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
            }
        }

        if (toolSpecifications != null && chatMemory == null) {
            throw illegalConfiguration("Please set up chatMemory in order to use tools");
        }

        Object proxyInstance = Proxy.newProxyInstance(
                aiServiceClass.getClassLoader(),
                new Class<?>[]{aiServiceClass},
                new InvocationHandler() {

                    private final ExecutorService executor = Executors.newCachedThreadPool();

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {

                        if (method.getDeclaringClass() == Object.class) {
                            return method.invoke(this, args);
                        }

                        validateParameters(method);

                        Optional<ChatMessage> systemMessage = prepareSystemMessage(method, args);
                        ChatMessage userMessage = prepareUserMessage(method, args);

                        if (retriever != null) {
                            List<TextSegment> relevant = retriever.findRelevant(userMessage.text());

                            if (relevant == null || relevant.isEmpty()) {
                                log.debug("No relevant information was found");
                            } else {
                                String relevantConcatenated = relevant.stream()
                                        .map(TextSegment::text)
                                        .collect(joining("\n\n"));

                                log.debug("Retrieved relevant information:\n" + relevantConcatenated + "\n");

                                userMessage = userMessage(userMessage.text()
                                        + "\n\nHere is some information that might be useful for answering:\n\n"
                                        + relevantConcatenated);
                            }
                        }

                        if (chatMemory != null) {
                            systemMessage.ifPresent(this::addIfNeeded);
                            chatMemory.add(userMessage);
                        }

                        List<ChatMessage> messages;
                        if (chatMemory != null) {
                            messages = chatMemory.messages();
                        } else {
                            messages = new ArrayList<>();
                            systemMessage.ifPresent(messages::add);
                            messages.add(userMessage);
                        }

                        Future<Result<Moderation>> moderationFuture = triggerModerationIfNeeded(method, messages);

                        Result<AiMessage> result = chatLanguageModel.sendMessages(messages, toolSpecifications);

                        verifyModerationIfNeeded(moderationFuture);

                        ToolExecutionRequest toolExecutionRequest;
                        while (true) { // TODO limit number of cycles

                            AiMessage aiMessage = result.get();

                            if (chatMemory != null) {
                                chatMemory.add(aiMessage);
                            }

                            toolExecutionRequest = aiMessage.toolExecutionRequest();
                            if (toolExecutionRequest == null) {
                                break;
                            }

                            ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());

                            log.debug("About to execute {}", toolExecutionRequest);
                            String toolExecutionResult = toolExecutor.execute(toolExecutionRequest.argumentsAsMap());
                            log.debug("Tool execution result: {}", toolExecutionResult);

                            ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.toolExecutionResultMessage(toolExecutionRequest.name(), toolExecutionResult);

                            chatMemory.add(toolExecutionResultMessage);

                            result = chatLanguageModel.sendMessages(chatMemory.messages());
                        }

                        return ServiceOutputParser.parse(result, method.getReturnType());
                    }

                    private Future<Result<Moderation>> triggerModerationIfNeeded(Method method, List<ChatMessage> messages) {
                        if (method.isAnnotationPresent(Moderate.class)) {
                            return executor.submit(() -> {
                                List<ChatMessage> messagesToModerate = removeToolMessages(messages);
                                return moderationModel.moderate(messagesToModerate);
                            });
                        }
                        return null;
                    }

                    private List<ChatMessage> removeToolMessages(List<ChatMessage> messages) {
                        return messages.stream()
                                .filter(it -> !(it instanceof ToolExecutionResultMessage))
                                .filter(it -> !(it instanceof AiMessage && ((AiMessage) it).toolExecutionRequest() != null))
                                .collect(toList());
                    }

                    private void verifyModerationIfNeeded(Future<Result<Moderation>> moderationFuture) {
                        if (moderationFuture != null) {
                            try {
                                Moderation moderation = moderationFuture.get().get();
                                if (moderation.flagged()) {
                                    throw new ModerationException(String.format("Text \"%s\" violates content policy", moderation.flaggedText()));
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    private void addIfNeeded(ChatMessage systemMessage) {
                        boolean shouldAddSystemMessage = true;
                        List<ChatMessage> messages = chatMemory.messages();
                        for (int i = messages.size() - 1; i >= 0; i--) {
                            if (messages.get(i) instanceof dev.langchain4j.data.message.SystemMessage) {
                                if (messages.get(i).equals(systemMessage)) {
                                    shouldAddSystemMessage = false;
                                }
                                break;
                            }
                        }
                        if (shouldAddSystemMessage) {
                            chatMemory.add(systemMessage);
                        }
                    }
                });

        return (T) proxyInstance;
    }

    private Optional<ChatMessage> prepareSystemMessage(Method method, Object[] args) {

        Parameter[] parameters = method.getParameters();
        Map<String, Object> variables = getPromptTemplateVariables(args, parameters);

        SystemMessage annotation = method.getAnnotation(SystemMessage.class);
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

    private static ChatMessage prepareUserMessage(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        Map<String, Object> variables = getPromptTemplateVariables(args, parameters);

        String outputFormatInstructions = outputFormatInstructions(method.getReturnType());

        String userName = getUserName(parameters, args);

        UserMessage annotation = method.getAnnotation(UserMessage.class);
        if (annotation != null) {
            String userMessageTemplate = String.join(annotation.delimiter(), annotation.value()) + outputFormatInstructions;

            if (userMessageTemplate.contains("{{it}}")) {
                if (parameters.length != 1) {
                    throw illegalConfiguration("Error: The {{it}} placeholder is present but the method does not have exactly one parameter. " +
                            "Please ensure that methods using the {{it}} placeholder have exactly one parameter.");
                }

                variables = singletonMap("it", toString(args[0]));
            }

            Prompt prompt = PromptTemplate.from(userMessageTemplate).apply(variables);
            return userMessage(userName, prompt.text());
        }

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(UserMessage.class)) {
                return userMessage(userName, toString(args[i]) + outputFormatInstructions);
            }
        }

        if (args == null || args.length == 0) {
            throw illegalConfiguration("Method should have at least one argument");
        }

        if (args.length == 1) {
            return userMessage(userName, toString(args[0]) + outputFormatInstructions);
        }

        throw illegalConfiguration("For methods with multiple parameters, each parameter must be annotated with @V, @UserMessage or @UserName");

    }

    private static String getUserName(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(UserName.class)) {
                return args[i].toString();
            }
        }
        return null;
    }

    private static void validateParameters(Method method) {
        Parameter[] parameters = method.getParameters();
        if (parameters == null || parameters.length < 2) {
            return;
        }

        for (Parameter parameter : parameters) {
            V v = parameter.getAnnotation(V.class);
            UserMessage userMessage = parameter.getAnnotation(UserMessage.class);
            if (v == null && userMessage == null) {
                throw illegalConfiguration("Parameter '%s' of method '%s' should be annotated either with @V or @UserMessage", parameter.getName(), method.getName());
            }
        }
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

    private static Object toString(Object arg) {
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
