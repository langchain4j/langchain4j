package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.cognisphere.DefaultCognisphere;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParallelAgentServiceImpl<T> extends AbstractService<T, ParallelAgentService<T>> implements ParallelAgentService<T> {

    private ExecutorService executorService;

    private ParallelAgentServiceImpl(Class<T> agentServiceClass) {
        super(agentServiceClass);
    }

    private static class DefaultExecutorHolder {
        private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool();
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

        private ParallelInvocationHandler(DefaultCognisphere cognisphere) {
            super(ParallelAgentServiceImpl.this, cognisphere);
        }

        @Override
        protected Object doAgentAction(DefaultCognisphere cognisphere) {
            parallelExecution(cognisphere);
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected InvocationHandler createSubAgentWithCognisphere(DefaultCognisphere cognisphere) {
            return new ParallelInvocationHandler(cognisphere);
        }

        private void parallelExecution(DefaultCognisphere cognisphere) {
            ExecutorService executors = executorService != null ? executorService : DefaultExecutorHolder.DEFAULT_EXECUTOR;
            var tasks = agentExecutors().stream()
                    .map(agentExecutor -> (Callable<Object>) () -> agentExecutor.invoke(cognisphere))
                    .toList();
            try {
                for (Future<?> future : executors.invokeAll(tasks)) {
                    future.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
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
        this.executorService = executorService;
        return this;
    }
}
