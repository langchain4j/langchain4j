package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.workflow.ParallelAgentService;
import dev.langchain4j.internal.DefaultExecutorProvider;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class ParallelAgentServiceImpl<T> extends AbstractService<T, ParallelAgentService<T>> implements ParallelAgentService<T> {

    private Executor executor;

    private ParallelAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
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
            Executor exec = executor != null ? executor : DefaultExecutorProvider.getDefaultExecutorService();
            var tasks = agentExecutors().stream()
                    .map(agentExecutor -> CompletableFuture.supplyAsync(() -> agentExecutor.execute(agenticScope), exec))
                    .toList();
            try {
                for (Future<?> future : tasks) {
                    future.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static ParallelAgentServiceImpl<UntypedAgent> builder() {
        return new ParallelAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> ParallelAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new ParallelAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    public ParallelAgentServiceImpl<T> executor(Executor executor) {
        this.executor = executor;
        return this;
    }
}
