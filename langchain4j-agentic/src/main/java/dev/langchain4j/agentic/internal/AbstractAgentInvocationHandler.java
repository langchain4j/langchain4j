package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.CognisphereOwner;
import dev.langchain4j.agentic.UntypedAgent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Map;

public abstract class AbstractAgentInvocationHandler implements InvocationHandler {
    protected final Class<?> agentServiceClass;
    protected final String outputName;

    protected Cognisphere cognisphere;

    protected AbstractAgentInvocationHandler(Class<?> agentServiceClass, String outputName) {
        this(agentServiceClass, outputName, null);
    }

    protected AbstractAgentInvocationHandler(Class<?> agentServiceClass, String outputName, Cognisphere cognisphere) {
        this.agentServiceClass = agentServiceClass;
        this.outputName = outputName;
        this.cognisphere = cognisphere;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == CognisphereOwner.class) {
            return switch (method.getName()) {
                case "cognisphere" -> cognisphere;
                case "withCognisphere" -> Proxy.newProxyInstance(
                        agentServiceClass.getClassLoader(),
                        new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                        createHandlerWithCognisphere((Cognisphere) args[0]));
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on ChatMemoryAccess class : " + method.getName());
            };
        }

        if (method.getDeclaringClass() == AgentInstance.class) {
            return switch (method.getName()) {
                case "trySetChatMemory" -> false;
                case "outputName" -> outputName;
                default ->
                        throw new UnsupportedOperationException(
                                "Unknown method on ChatMemoryAccess class : " + method.getName());
            };
        }

        if (cognisphere == null) {
            cognisphere = new Cognisphere();
        }

        if (method.getDeclaringClass() == UntypedAgent.class) {
            cognisphere.writeStates((Map<String, Object>) args[0]);
        } else {
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                int index = i;
                AgentSpecification.optionalParameterName(parameters[i])
                        .ifPresent(argName -> cognisphere.writeState(argName, args[index]) );
            }
        }

        Object result = doAgentAction();
        return outputName != null ? cognisphere.readState(outputName) : result;
    }

    protected abstract Object doAgentAction();

    protected abstract InvocationHandler createHandlerWithCognisphere(Cognisphere cognisphere);
}
