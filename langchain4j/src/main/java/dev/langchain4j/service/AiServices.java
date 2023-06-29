package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Result;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.ServiceOutputParser.outputFormatInstructions;
import static java.util.Collections.singletonMap;

public class AiServices<T> {

    private final Class<T> aiServiceClass;
    private ChatLanguageModel chatLanguageModel;
    private ChatMemory chatMemory;
    private ModerationModel moderationModel;

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

        Object proxyInstance = Proxy.newProxyInstance(
                aiServiceClass.getClassLoader(),
                new Class<?>[]{aiServiceClass},
                new InvocationHandler() {

                    private final ExecutorService executor = Executors.newCachedThreadPool();

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {

                        validateParameters(method);

                        Optional<ChatMessage> systemMessage = prepareSystemMessage(method, args);
                        ChatMessage userMessage = prepareUserMessage(method, args);

                        if (chatMemory != null) {
                            systemMessage.ifPresent(it -> {
                                boolean shouldAddSystemMessage = true;
                                List<ChatMessage> messages = chatMemory.messages();
                                for (int i = messages.size() - 1; i >= 0; i--) {
                                    if (messages.get(i) instanceof dev.langchain4j.data.message.SystemMessage) {
                                        if (messages.get(i).equals(it)) {
                                            shouldAddSystemMessage = false;
                                        }
                                        break;
                                    }
                                }
                                if (shouldAddSystemMessage) {
                                    chatMemory.add(it);
                                }
                            });

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
                        Result<AiMessage> result = chatLanguageModel.sendMessages(messages);
                        verifyModerationIfNeeded(moderationFuture);

                        if (chatMemory != null) {
                            chatMemory.add(result.get());
                        }

                        return ServiceOutputParser.parse(result, method.getReturnType());
                    }

                    private Future<Result<Moderation>> triggerModerationIfNeeded(Method method, List<ChatMessage> messages) {
                        if (method.isAnnotationPresent(Moderate.class)) {
                            return executor.submit(() -> moderationModel.moderate(messages));
                        }
                        return null;
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
                });

        return (T) proxyInstance;
    }

    private Optional<ChatMessage> prepareSystemMessage(Method method, Object[] args) {

        Parameter[] parameters = method.getParameters();
        Map<String, Object> variables = getPromptTemplateVariables(args, parameters);

        if (method.isAnnotationPresent(SystemMessage.class)) {
            SystemMessage annotation = method.getAnnotation(SystemMessage.class);

            String systemMessageTemplate = String.join("\n", annotation.value());
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

        UserMessage userMessage = method.getAnnotation(UserMessage.class);
        if (userMessage != null) {
            String userMessageTemplate = String.join("\n", userMessage.value()) + outputFormatInstructions;

            if (userMessageTemplate.contains("{{it}}")) {
                if (parameters.length != 1) {
                    throw illegalConfiguration("Error: The {{it}} placeholder is present but the method does not have exactly one parameter. " +
                            "Please ensure that methods using the {{it}} placeholder have exactly one parameter.");
                }

                variables = singletonMap("it", toString(args[0]));
            }

            Prompt prompt = PromptTemplate.from(userMessageTemplate).apply(variables);
            return prompt.toUserMessage();
        }

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(UserMessage.class)) {
                return userMessage(toString(args[i]) + outputFormatInstructions);
            }
        }

        if (args == null || args.length == 0) {
            throw illegalConfiguration("Method should have at least one argument");
        }

        if (args.length == 1) {
            return userMessage(toString(args[0]) + outputFormatInstructions);
        }

        throw illegalConfiguration("For methods with multiple arguments, each argument must be annotated with either @V or @UserMessage. " +
                "Please ensure all arguments in multi-argument methods have one of these annotations.");

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
