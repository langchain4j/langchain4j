package dev.langchain4j.agentic.workflow;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentInstance;
import dev.langchain4j.agentic.internal.CognisphereOwner;

public class ParallelAgentService<T> implements OutputtingService<ParallelAgentService<T>> {

    private final Class<T> agentServiceClass;

    private final List<AgentExecutor> agentExecutors = new ArrayList<>();

    private String outputName;
    private Function<Cognisphere, Object> output = cognisphere -> null;

    private BiConsumer<List<AgentExecutor>, Cognisphere> executor =
            (agentExecutors, cognisphere) -> agentExecutors.stream().parallel().forEach(agentExecutor -> agentExecutor.invoke(cognisphere));;

    private ParallelAgentService(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
    }

    public T build() {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {agentServiceClass, AgentInstance.class, CognisphereOwner.class},
                new ParallelInvocationHandler());
    }

    private class ParallelInvocationHandler extends AbstractAgentInvocationHandler {

        private ParallelInvocationHandler() {
            super(ParallelAgentService.this.agentServiceClass, ParallelAgentService.this.outputName);
        }

        private ParallelInvocationHandler(Cognisphere cognisphere) {
            super(ParallelAgentService.this.agentServiceClass, ParallelAgentService.this.outputName, cognisphere);
        }

        @Override
        protected Object doAgentAction(Cognisphere cognisphere) {
            executor.accept(agentExecutors, cognisphere);
            return result(cognisphere, output.apply(cognisphere));
        }

        @Override
        protected CognisphereOwner createSubAgentWithCognisphere(Cognisphere cognisphere) {
            return new ParallelInvocationHandler(cognisphere);
        }
    }

    public static ParallelAgentService<UntypedAgent> builder() {
        return builder(UntypedAgent.class);
    }

    public static <T> ParallelAgentService<T> builder(Class<T> agentServiceClass) {
        return new ParallelAgentService<>(agentServiceClass);
    }

    public ParallelAgentService<T> subAgents(Object... agents) {
        return subAgents(agentsToExecutors(agents));
    }

    public ParallelAgentService<T> subAgents(List<AgentExecutor> agentExecutors) {
        this.agentExecutors.addAll(agentExecutors);
        return this;
    }

    public ParallelAgentService<T> executorService(ExecutorService executorService) {
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

    @Override
    public ParallelAgentService<T> outputName(String outputName) {
        this.outputName = outputName;
        return this;
    }

    @Override
    public ParallelAgentService<T> output(Function<Cognisphere, Object> output) {
        this.output = output;
        return this;
    }
}
