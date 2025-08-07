package dev.langchain4j.agentic.a2a;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.AgentSpecification;
import io.a2a.A2A;
import io.a2a.client.A2AClient;
import io.a2a.spec.A2AClientError;
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

public class DefaultA2AClientBuilder<T> implements A2AClientBuilder<T> {

    private final Class<T> agentServiceClass;

    private final AgentCard agentCard;
    private final A2AClient a2aClient;

    private String[] inputNames;
    private String outputName;

    DefaultA2AClientBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
        this.agentCard = agentCard(a2aServerUrl);
        this.a2aClient = new A2AClient(agentCard);
        this.agentServiceClass = agentServiceClass;
    }

    private static AgentCard agentCard(String a2aServerUrl) {
        try {
            return A2A.getAgentCard(a2aServerUrl);
        } catch (A2AClientError e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T build() {
        if (agentServiceClass == UntypedAgent.class && inputNames == null) {
            throw new IllegalArgumentException("Input names must be provided for UntypedAgent.");
        }

        Object agent = Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, A2AClientSpecification.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                        if (method.getDeclaringClass() == AgentSpecification.class) {
                            return switch (method.getName()) {
                                case "outputName" -> outputName;
                                default ->
                                        throw new UnsupportedOperationException(
                                                "Unknown method on AgentInstance class : " + method.getName());
                            };
                        }

                        if (method.getDeclaringClass() == A2AClientSpecification.class) {
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
                .role(Message.Role.USER)
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
                .collect(Collectors.joining("\n"));
    }

    @Override
    public DefaultA2AClientBuilder<T> inputNames(String... inputNames) {
        this.inputNames = inputNames;
        return this;
    }

    @Override
    public DefaultA2AClientBuilder<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }
}
