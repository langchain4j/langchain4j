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

public class AiServiceBuilder<T> {

    private final Class<T> aiServiceClass;
    private ChatLanguageModel chatLanguageModel;
    private ChatMemory chatMemory;
    private ModerationModel moderationModel;

    private AiServiceBuilder(Class<T> aiServiceClass) {
        this.aiServiceClass = aiServiceClass;
    }

    public static <T> AiServiceBuilder<T> forClass(Class<T> aiServiceClass) {
        return new AiServiceBuilder<>(aiServiceClass);
    }

    public AiServiceBuilder<T> chatLanguageModel(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
        return this;
    }

    public AiServiceBuilder<T> chatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        return this;
    }

    public AiServiceBuilder<T> moderationModel(ModerationModel moderationModel) {
        this.moderationModel = moderationModel;
        return this;
    }

    public T build() {

        // TODO validate all builder fields
        Object proxyInstance = Proxy.newProxyInstance(
                aiServiceClass.getClassLoader(),
                new Class<?>[]{aiServiceClass},
                new InvocationHandler() {

                    private final ExecutorService executor = Executors.newCachedThreadPool();

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {

                        validateParameters(method);

                        List<ChatMessage> messages = prepareMessages(method, args);

                        Future<Result<Moderation>> moderationFuture = null;
                        if (method.isAnnotationPresent(Moderate.class)) {
                            // TODO validate that moderation model is set
                            moderationFuture = executor.submit(() -> moderationModel.moderate(messages));
                        }

                        List<ChatMessage> messagesToSend = new ArrayList<>(messages);

                        if (chatMemory != null) {
                            // TODO make sure only one system message is stored.
                            // TODO Which one exactly if there are many methods with @SystemMessage?
                            // TODO Swap system message each time?
                            messagesToSend.forEach(chatMemory::add);
                            messagesToSend = chatMemory.messages();
                        }

                        Result<AiMessage> result = chatLanguageModel.sendMessages(messagesToSend);

                        if (chatMemory != null) {
                            chatMemory.add(result.get());
                        }

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

                        return ServiceOutputParser.parse(result, method.getReturnType());
                    }
                });

        return (T) proxyInstance;
    }

    private static List<ChatMessage> prepareMessages(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();

        Map<String, Object> variables = getPromptTemplateVariables(args, parameters);

        List<ChatMessage> messages = new ArrayList<>();

        SystemMessage systemMessage = method.getAnnotation(SystemMessage.class);
        if (systemMessage != null) {
            String systemMessageTemplate = String.join("\n", systemMessage.value());
            if (systemMessageTemplate.isEmpty()) {
                throw illegalConfiguration("@SystemMessage's template cannot be empty");
            }
            Prompt prompt = PromptTemplate.from(systemMessageTemplate).apply(variables);
            messages.add(prompt.toSystemMessage());
        }

        String outputFormatInstructions = outputFormatInstructions(method.getReturnType());

        UserMessage userMessage = method.getAnnotation(UserMessage.class);
        if (userMessage != null) {
            String userMessageTemplate = String.join("\n", userMessage.value()) + outputFormatInstructions;

            if (userMessageTemplate.contains("{{it}}")) {
                if (parameters.length != 1) {
                    throw illegalConfiguration("{{it}} placeholder can be used only with a single parameter method"); // TODO message
                }

                variables = singletonMap("it", toString(args[0]));
            }

            Prompt prompt = PromptTemplate.from(userMessageTemplate).apply(variables);
            messages.add(prompt.toUserMessage());
        }

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            UserMessage parameterAsUserMessage = parameter.getAnnotation(UserMessage.class);
            if (parameterAsUserMessage != null) {
                if (parameters[i].getType().isAnnotationPresent(StructuredPrompt.class)) {
                    Prompt prompt = StructuredPromptProcessor.toPrompt(args[i]);
                    messages.add(userMessage(prompt.text() + outputFormatInstructions));
                } else {
                    String userMessagePrompt = toString(args[i]) + outputFormatInstructions;
                    messages.add(userMessage(userMessagePrompt));
                }
            }
        }
        // TODO do not allow multiple @UserMessage in one method

        if (messages.isEmpty() || !(messages.get(messages.size() - 1) instanceof dev.langchain4j.data.message.UserMessage)) {
            if (args.length == 1) {
                Object argument = args[0];
                if (argument.getClass() == String.class) {
                    messages.add(userMessage(toString(argument) + outputFormatInstructions));
                } else if (parameters[0].getType().isAnnotationPresent(StructuredPrompt.class)) {
                    Prompt prompt = StructuredPromptProcessor.toPrompt(argument);
                    messages.add(userMessage(prompt.text() + outputFormatInstructions));
                }
            } else {
                // TODO throw?
            }
        }
        return messages;
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
