package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
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
                new Class<?>[] {agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class},
                new ParallelInvocationHandler());
    }

    private class ParallelInvocationHandler extends AbstractAgentInvocationHandler {

        private ParallelInvocationHandler() {
            super(ParallelAgentServiceImpl.this);
        }

        private ParallelInvocationHandler(DefaultAgenticScope agenticScope) {
            super(ParallelAgentServiceImpl.this, agenticScope);
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            parallelExecution(agenticScope);
            return result(agenticScope, output.apply(agenticScope));
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new ParallelInvocationHandler(agenticScope);
        }

        private void parallelExecution(DefaultAgenticScope agenticScope) {
            ExecutorService executors = executorService != null ? executorService : DefaultExecutorHolder.DEFAULT_EXECUTOR;
            var tasks = agentExecutors().stream()
                    .map(agentExecutor -> (Callable<Object>) () -> agentExecutor.execute(agenticScope))
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
