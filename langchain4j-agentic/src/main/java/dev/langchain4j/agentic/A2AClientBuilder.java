package dev.langchain4j.agentic;

import dev.langchain4j.agentic.internal.A2AClientInstance;
import dev.langchain4j.agentic.internal.AgentInstance;
import io.a2a.client.A2AClient;
import io.a2a.spec.A2AServerException;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Part;
import io.a2a.spec.SendMessageResponse;
import io.a2a.spec.Task;
import io.a2a.spec.TextPart;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class A2AClientBuilder<T> {

    private final Class<T> agentServiceClass;

    private final AgentCard agentCard;
    private final A2AClient a2aClient;

    private String[] inputNames;
    private String outputName;

    A2AClientBuilder(AgentCard agentCard, Class<T> agentServiceClass) {
        this.agentCard = agentCard;
        this.a2aClient = new A2AClient(agentCard);
        this.agentServiceClass = agentServiceClass;
    }

    public T build() {
        if (agentServiceClass == UntypedAgent.class && inputNames == null) {
            throw new IllegalArgumentException("Input names must be provided for UntypedAgent.");
        }

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, A2AClientInstance.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                        if (method.getDeclaringClass() == AgentInstance.class) {
                            return switch (method.getName()) {
                                case "outputName" -> outputName;
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unknown method on AgentInstance class : " + method.getName());
                            };
                        }

                        if (method.getDeclaringClass() == A2AClientInstance.class) {
                            return switch (method.getName()) {
                                case "agentCard" -> agentCard;
                                case "inputNames" -> inputNames;
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unknown method on A2AClientInstance class : " + method.getName());
                            };
                        }

                        return invokeAgent(args);
                    }
                });

        return (T) agent;
    }

    private Object invokeAgent(Object[] args) throws A2AServerException {
        List<Part<?>> parts = new ArrayList<>();

        if (agentServiceClass == UntypedAgent.class) {
            Map<String, Object> params = (Map<String, Object>) args[0];
            for (String inputName : inputNames) {
                parts.add(new TextPart(params.get(inputName).toString()));
            }
        } else {
            for (Object arg : args) {
                parts.add(new TextPart(arg.toString()));
            }
        }

        Message message = new Message.Builder()
                .role(Message.Role.AGENT)
                .parts(parts)
                .build();

        MessageSendParams params = new MessageSendParams.Builder()
                .message(message)
                .build();

        SendMessageResponse response = a2aClient.sendMessage(params);

        return ((Task)response.getResult()).getArtifacts().stream()
                .flatMap(a -> a.parts().stream())
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::getText)
                .collect(Collectors.joining());
    }

    public A2AClientBuilder<T> inputNames(String... inputNames) {
        this.inputNames = inputNames;
        return this;
    }

    public A2AClientBuilder<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }
}
