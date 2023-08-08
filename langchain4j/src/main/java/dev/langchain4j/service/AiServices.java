package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.retriever.Retriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.service.ServiceOutputParser.outputFormatInstructions;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * AI Services provide a simpler and more flexible alternative to chains.
 * You can define your own API (a Java interface with one or more methods),
 * and AiServices will provide an implementation for it (we call this "AI Service").
 * <p>
 * Currently, AI Services support:
 * <pre>
 * - Prompt templates for user and system messages using {@code @UserMessage} and {@code @SystemMessage}
 * - Structured prompts as method arguments (see {@code @StructuredPrompt})
 * - Shared or per-user (see {@code @UserId}) chat memory
 * - Retrievers
 * - Tools (see {@code @Tool})
 * - Various return types (output parsers), see below
 * - Streaming (use {@code TokenStream} as a return type)
 * - Auto-moderation using {@code @Moderate}
 * </pre>
 * <p>
 * Here is the simplest example of an AI Service:
 *
 * <pre>
 * interface Assistant {
 *
 *     String chat(String userMessage);
 * }
 *
 * Assistant assistant = AiServices.create(Assistant.class, model);
 *
 * String answer = assistant.chat("hello");
 * System.out.println(answer); // Hello, how can I help you today?
 * </pre>
 *
 * <pre>
 * The return type of methods in your AI Service can be any of the following:
 * - a {@code String} or an {@code AiMessage}, if you want to get the answer from the LLM as-is
 * - a {@code List<String>} or {@code Set<String>}, if you want to receive the answer as a collection of items or bullet points
 * - any {@code Enum} or a {@code boolean}, if you want to use the LLM for classification
 * - a primitive or boxed Java type: {@code int}, {@code Double}, etc., if you want to use the LLM for data extraction
 * - many default Java types: {@code Date}, {@code LocalDateTime}, {@code BigDecimal}, etc., if you want to use the LLM for data extraction
 * - any custom POJO, if you want to use the LLM for data extraction
 * </pre>
 * <p>
 * Let's see how we can classify the sentiment of a text:
 * <pre>
 * enum Sentiment {
 *     POSITIVE, NEUTRAL, NEGATIVE
 * }
 *
 * interface SentimentAnalyzer {
 *
 *     {@code @UserMessage}("Analyze sentiment of {{it}}")
 *     Sentiment analyzeSentimentOf(String text);
 * }
 *
 * SentimentAnalyzer assistant = AiServices.create(SentimentAnalyzer.class, model);
 *
 * Sentiment sentiment = analyzeSentimentOf.chat("I love you");
 * System.out.println(sentiment); // POSITIVE
 * </pre>
 * <p>
 * As demonstrated, you can put {@code @UserMessage} and {@code @SystemMessage} annotations above a method to define
 * templates for user and system messages, respectively.
 * In this example, the special {@code {{it}}} prompt template variable is used because there's only one method parameter.
 * However, you can use more parameters as demonstrated in the following example:
 * <pre>
 * interface Translator {
 *
 *     {@code @SystemMessage}("You are a professional translator into {{language}}")
 *     {@code @UserMessage}("Translate the following text: {{text}}")
 *     String translate(@V("text") String text, @V("language") String language);
 * }
 * </pre>
 * <p>
 * See more examples <a href="https://github.com/langchain4j/langchain4j-examples/tree/main/other-examples/src/main/java">here</a>.
 *
 * @param <T> The interface for which AiServices will provide an implementation.
 */
public class AiServices<T> {

    private final Logger log = LoggerFactory.getLogger(AiServices.class);

    private static final String SHARED = "shared";

    private final AiServiceContext context = new AiServiceContext();

    private AiServices(Class<T> aiServiceClass) {
        context.aiServiceClass = aiServiceClass;
    }

    /**
     * Creates an AI Service (an implementation of the provided interface), that is backed by the provided chat model.
     * This convenience method can be used to create simple AI Services.
     * For more complex cases, please use {@code builder()}.
     *
     * @param aiService         The class of the interface to be implemented.
     * @param chatLanguageModel The chat model to be used under the hood.
     * @return An instance of the provided interface, implementing all its defined methods.
     */
    public static <T> T create(Class<T> aiService, ChatLanguageModel chatLanguageModel) {
        return builder(aiService)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    /**
     * Creates an AI Service (an implementation of the provided interface), that is backed by the provided streaming chat model.
     * This convenience method can be used to create simple AI Services.
     * For more complex cases, please use {@code builder()}.
     *
     * @param aiService                  The class of the interface to be implemented.
     * @param streamingChatLanguageModel The streaming chat model to be used under the hood.
     *                                   The return type of all methods should be {@code TokenStream}.
     * @return An instance of the provided interface, implementing all its defined methods.
     */
    public static <T> T create(Class<T> aiService, StreamingChatLanguageModel streamingChatLanguageModel) {
        return builder(aiService)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .build();
    }

    /**
     * Begins the construction of an AI Service.
     *
     * @param aiService The class of the interface to be implemented.
     * @return A builder instance. Once you have configured all necessary components, call {@code build()}.
     */
    public static <T> AiServices<T> builder(Class<T> aiService) {
        return new AiServices<>(aiService);
    }

    /**
     * Configures chat model that will be used under the hood of the AI Service.
     * Either {@code ChatLanguageModel} or {@code StreamingChatLanguageModel} should be configured,
     * but not both at the same time.
     *
     * @param chatLanguageModel Chat model that will be used under the hood of the AI Service.
     * @return A builder instance. Once you have configured all necessary components, call {@code build()}.
     */
    public AiServices<T> chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        context.chatLanguageModel = chatLanguageModel;
        return this;
    }

    /**
     * Configures streaming chat model that will be used under the hood of the AI Service.
     * Either {@code ChatLanguageModel} or {@code StreamingChatLanguageModel} should be configured,
     * but not both at the same time.
     *
     * @param streamingChatLanguageModel Streaming chat model that will be used under the hood of the AI Service.
     * @return A builder instance. Once you have configured all necessary components, call {@code build()}.
     */
    public AiServices<T> streamingChatLanguageModel(StreamingChatLanguageModel streamingChatLanguageModel) {
        context.streamingChatLanguageModel = streamingChatLanguageModel;
        return this;
    }

    /**
     * Configures the chat memory that will be used to preserve conversation history between method calls.
     * Unless a {@code ChatMemory} or chat memory supplier is configured, all method calls will be stateless.
     * In other words, the LLM will not remember the conversation from the previous method call.
     * The same {@code ChatMemory} instance will be used for every method call.
     * If you want to use a separate {@code ChatMemory} for each user, configure a chat memory supplier instead (method below).
     * Either a {@code ChatMemory} or a chat memory supplier can be configured, but not both simultaneously.
     *
     * @param chatMemory An instance of chat memory to be used by the AI Service.
     * @return A builder instance. After configuring all necessary components, call {@code build()}.
     */
    public AiServices<T> chatMemory(ChatMemory chatMemory) {
        context.chatMemories = new ConcurrentHashMap<>();
        context.chatMemories.put(SHARED, chatMemory);
        return this;
    }

    /**
     * Configures the chat memory supplier, which will be used to create a dedicated instance of {@code ChatMemory} for each user.
     * A new instance of chat memory will be automatically created for each new user by invoking {@code chatMemorySupplier.get()}.
     * To distinguish between users, one of the method's arguments should be a user ID (of any data type)
     * annotated with {@code @UserId}. This ID will be used to retrieve the respective user's chat memory.
     * If you prefer to use the same (shared) {@code ChatMemory} for all users, configure a chat memory instead (method above).
     * Either a {@code ChatMemory} or a chat memory supplier should be configured, but not both simultaneously.
     *
     * @param chatMemorySupplier The supplier used to create a {@code ChatMemory} for each new user.
     * @return A builder instance. After configuring all necessary components, call {@code build()}.
     */
    public AiServices<T> chatMemorySupplier(Supplier<ChatMemory> chatMemorySupplier) {
        context.chatMemories = new ConcurrentHashMap<>();
        context.chatMemorySupplier = chatMemorySupplier;
        return this;
    }

    /**
     * Configures a moderation model to be used for automatic content moderation.
     * If a method in the AI Service is annotated with {@code @Moderate}, the moderation model will be invoked
     * to check the user content for any inappropriate or harmful material. See @Moderate for more details.
     *
     * @param moderationModel The moderation model to be used for content moderation.
     * @return A builder instance. Once you have configured all necessary components, call {@code build()}.
     * @see Moderate
     */
    public AiServices<T> moderationModel(ModerationModel moderationModel) {
        context.moderationModel = moderationModel;
        return this;
    }

    /**
     * Configures the tools that the LLM can use.
     *
     * @param objectsWithTools One or more objects whose methods are annotated with {@code @Tool}.
     *                         All these tools (methods annotated with {@code @Tool}) are accessible to the LLM.
     *                         Note that inherited methods are not considered.
     * @return A builder instance. After configuring all necessary components, invoke {@code build()}.
     * @see Tool
     */
    public AiServices<T> tools(Object... objectsWithTools) {
        return tools(Arrays.asList(objectsWithTools));
    }

    /**
     * Configures the tools that the LLM can use.
     *
     * @param objectsWithTools A list of objects whose methods are annotated with {@code @Tool}.
     *                         All these tools (methods annotated with {@code @Tool}) are accessible to the LLM.
     *                         Note that inherited methods are not considered.
     * @return A builder instance. After configuring all necessary components, invoke {@code build()}.
     * @see Tool
     */
    public AiServices<T> tools(List<Object> objectsWithTools) {
        context.toolSpecifications = new ArrayList<>();
        context.toolExecutors = new HashMap<>();

        for (Object objectWithTool : objectsWithTools) {
            for (Method method : objectWithTool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    ToolSpecification toolSpecification = toolSpecificationFrom(method);
                    context.toolSpecifications.add(toolSpecification);
                    context.toolExecutors.put(toolSpecification.name(), new ToolExecutor(objectWithTool, method));
                }
            }
        }

        return this;
    }

    // TODO separate retriever per user
    // TODO way to configure custom prompt with original message and context
    // TODO callback to transform/filter retrieved segments

    /**
     * Configures a retriever that will be invoked on every method call to fetch relevant information
     * related to the current user message from an underlying source (e.g., embedding store).
     * This relevant information is automatically injected into the message sent to the LLM.
     *
     * @param retriever The retriever to be used by the AI Service.
     * @return A builder instance. After configuring all necessary components, call {@code build()}.
     */
    public AiServices<T> retriever(Retriever<TextSegment> retriever) {
        context.retriever = retriever;
        return this;
    }

    /**
     * Constructs and returns the AI Service.
     *
     * @return An instance of the AI Service implementing the specified interface.
     */
    public T build() {

        if (context.chatLanguageModel == null && context.streamingChatLanguageModel == null) {
            throw illegalConfiguration("Please specify either chatLanguageModel or streamingChatLanguageModel");
        }

        for (Method method : context.aiServiceClass.getMethods()) {
            if (method.isAnnotationPresent(Moderate.class) && context.moderationModel == null) {
                throw illegalConfiguration("The @Moderate annotation is present, but the moderationModel is not set up. " +
                        "Please ensure a valid moderationModel is configured before using the @Moderate annotation.");
            }
        }

        if (context.toolSpecifications != null && !context.hasChatMemory()) {
            throw illegalConfiguration("Please set up chatMemory or chatMemorySupplier in order to use tools");
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

                        Optional<ChatMessage> systemMessage = prepareSystemMessage(method, args);
                        ChatMessage userMessage = prepareUserMessage(method, args);

                        if (context.retriever != null) { // TODO extract method/class
                            List<TextSegment> relevant = context.retriever.findRelevant(userMessage.text());

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

                        Object userId = getUserId(method, args).orElse(SHARED);

                        if (context.hasChatMemory()) {
                            ChatMemory chatMemory = context.chatMemoryOf(userId);
                            systemMessage.ifPresent(it -> addIfNeeded(it, chatMemory));
                            chatMemory.add(userMessage);
                        }

                        List<ChatMessage> messages;
                        if (context.hasChatMemory()) {
                            messages = context.chatMemoryOf(userId).messages();
                        } else {
                            messages = new ArrayList<>();
                            systemMessage.ifPresent(messages::add);
                            messages.add(userMessage);
                        }

                        Future<Moderation> moderationFuture = triggerModerationIfNeeded(method, messages);

                        if (method.getReturnType() == TokenStream.class) {
                            return new AiServiceTokenStream(messages, context, userId); // TODO moderation
                        }

                        AiMessage aiMessage = context.chatLanguageModel.sendMessages(messages, context.toolSpecifications);

                        verifyModerationIfNeeded(moderationFuture);

                        ToolExecutionRequest toolExecutionRequest;
                        while (true) { // TODO limit number of cycles

                            if (context.hasChatMemory()) {
                                context.chatMemoryOf(userId).add(aiMessage);
                            }

                            toolExecutionRequest = aiMessage.toolExecutionRequest();
                            if (toolExecutionRequest == null) {
                                break;
                            }

                            ToolExecutor toolExecutor = context.toolExecutors.get(toolExecutionRequest.name());
                            String toolExecutionResult = toolExecutor.execute(toolExecutionRequest);
                            ToolExecutionResultMessage toolExecutionResultMessage
                                    = toolExecutionResultMessage(toolExecutionRequest.name(), toolExecutionResult);

                            ChatMemory chatMemory = context.chatMemoryOf(userId);
                            chatMemory.add(toolExecutionResultMessage);

                            aiMessage = context.chatLanguageModel.sendMessages(chatMemory.messages());
                        }

                        return ServiceOutputParser.parse(aiMessage, method.getReturnType());
                    }

                    private Future<Moderation> triggerModerationIfNeeded(Method method, List<ChatMessage> messages) {
                        if (method.isAnnotationPresent(Moderate.class)) {
                            return executor.submit(() -> {
                                List<ChatMessage> messagesToModerate = removeToolMessages(messages);
                                return context.moderationModel.moderate(messagesToModerate);
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

                    private void verifyModerationIfNeeded(Future<Moderation> moderationFuture) {
                        if (moderationFuture != null) {
                            try {
                                Moderation moderation = moderationFuture.get();
                                if (moderation.flagged()) {
                                    throw new ModerationException(String.format("Text \"%s\" violates content policy", moderation.flaggedText()));
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    private void addIfNeeded(ChatMessage systemMessage, ChatMemory chatMemory) { // TODO move to memory?
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

    private Optional<Object> getUserId(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(UserId.class)) {
                Object userId = args[i];
                if (userId == null) {
                    throw illegalArgument("The value of parameter %s annotated with @UserId in method %s must not be null",
                            parameters[i].getName(), method.getName());
                }
                return Optional.of(userId);
            }
        }
        return Optional.empty();
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

        throw illegalConfiguration("For methods with multiple parameters, each parameter must be annotated with @V, @UserMessage, @UserId or @UserName");
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
            UserId userId = parameter.getAnnotation(UserId.class);
            UserName userName = parameter.getAnnotation(UserName.class);
            if (v == null && userMessage == null && userId == null && userName == null) {
                throw illegalConfiguration(
                        "Parameter '%s' of method '%s' should be annotated with @V or @UserMessage or @UserId or @UserName",
                        parameter.getName(), method.getName()
                );
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
