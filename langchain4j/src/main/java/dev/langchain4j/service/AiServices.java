package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.output.Result;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.service.ServiceOutputParser.outputFormatInstructions;

public class AiServices {

    public static <T> T create(Class<T> aiServiceClass, ChatLanguageModel chatLanguageModel) {

        Object proxyInstance = Proxy.newProxyInstance(
                aiServiceClass.getClassLoader(),
                new Class<?>[]{aiServiceClass},
                (proxy, method, args) -> {

                    Parameter[] parameters = method.getParameters();
                    Class<?> returnType = method.getReturnType();

                    validateParameters(method);

                    Map<String, Object> variables = getPromptVariables(args, parameters);

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

                    String outputFormatInstructions = outputFormatInstructions(returnType);

                    UserMessage userMessage = method.getAnnotation(UserMessage.class);
                    if (userMessage != null) {
                        String userMessageTemplate = String.join("\n", userMessage.value()) + outputFormatInstructions;
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

                    if (messages.isEmpty() || !(messages.get(messages.size() - 1) instanceof dev.langchain4j.data.message.UserMessage)) {
                        if (args == null || args.length == 0) {
                            String instruction = convertCamelCase(method.getName());
                            messages.add(userMessage(instruction + outputFormatInstructions));
                        } else if (args.length == 1) {
                            Object argument = args[0];
                            if (parameters[0].getType().isAnnotationPresent(StructuredPrompt.class)) {
                                Prompt prompt = StructuredPromptProcessor.toPrompt(argument);
                                messages.add(userMessage(prompt.text() + outputFormatInstructions));
                            } else {
                                String instruction = convertCamelCase(method.getName()) + ": \"" + toString(argument) + "\"";
                                messages.add(userMessage(instruction + outputFormatInstructions));
                            }
                        }
                    }

                    Result<AiMessage> result = chatLanguageModel.sendMessages(messages);

                    return ServiceOutputParser.parse(result, returnType);
                });

        return (T) proxyInstance;
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

    private static Map<String, Object> getPromptVariables(Object[] args, Parameter[] parameters) {
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

    private static String convertCamelCase(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append(" ");
            }
            result.append(c);
        }
        return result.toString().toLowerCase();
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
