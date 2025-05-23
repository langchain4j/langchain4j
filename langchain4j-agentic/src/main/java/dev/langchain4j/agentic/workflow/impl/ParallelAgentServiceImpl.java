package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class ParallelAgentServiceImpl<T> extends AbstractService<T, ParallelAgentService<T>> implements ParallelAgentService<T> {

    private BiConsumer<List<AgentExecutor>, Cognisphere> executor =
            (agentExecutors, cognisphere) -> agentExecutors.stream().parallel().forEach(agentExecutor -> agentExecutor.invoke(cognisphere));;

    private ParallelAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    @Override
    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new ParallelInvocationHandler());
    }

    private class ParallelInvocationHandler extends AbstractAgentInvocationHandler {

        private ParallelInvocationHandler() {
            super(ParallelAgentServiceImpl.this);
        }

        private ParallelInvocationHandler(Cognisphere cognisphere) {
            super(ParallelAgentServiceImpl.this, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            executor.accept(agentExecutors(), cognisphere);
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
            return new ParallelInvocationHandler(cognisphere);
        }
    }

    public static ParallelAgentServiceImpl<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> ParallelAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ParallelAgentServiceImpl<>(agentServiceClass);
    }

    @Override
    public ParallelAgentServiceImpl<T> executorService(ExecutorService executorService) {
        this.executor = (agentExecutors, cognisphere) -> {
            var tasks = agentExecutors.stream()
                    .map(agentExecutor -> (Callable<Object>) () -> agentExecutor.invoke(cognisphere))
                    .toList();
            try {
                for (Future<?> future : executorService.invokeAll(tasks)) {
                    future.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        };
        return this;
    }
}
