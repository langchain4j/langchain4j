package dev.langchain4j.agentic.p2p;

import dev.langchain4j.agentic.internal.AbstractAgentInvocationHandler;
import dev.langchain4j.agentic.internal.AbstractService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.AgentSpecification;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.internal.DefaultExecutorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class P2PAgentServiceImpl<T> extends AbstractService<T, P2PAgentServiceImpl<T>>
        implements P2PAgentService<T> {

    private static final Logger LOG = LoggerFactory.getLogger(P2PAgentServiceImpl.class);

    private int maxAgentsInvocations = 10;

    private BiPredicate<AgenticScope, Integer> exitCondition = (scope, invocationsCounter) -> false;

    private final List<AgentExecutor> agents = new ArrayList<>();

    private Executor executor;

    private P2PAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    public T build() {
        return build(null);
    }

    T build(DefaultAgenticScope agenticScope) {
        return (T) Proxy.newProxyInstance(
                agentServiceClass.getClassLoader(),
                new Class<?>[] {
                    agentServiceClass, AgentSpecification.class, AgenticScopeOwner.class, AgenticScopeAccess.class
                },
                new P2PInvocationHandler(agenticScope, agents));
    }

    private class P2PInvocationHandler extends AbstractAgentInvocationHandler {

        private final List<AgentExecutor> agents;
        private final List<AgentActivator> agentActivators;

        private final AtomicInteger invocationCounter = new AtomicInteger(0);

        private final Executor exec = executor != null ? executor : DefaultExecutorProvider.getDefaultExecutorService();

        private volatile boolean terminating = false;

        public P2PInvocationHandler(DefaultAgenticScope agenticScope, List<AgentExecutor> agents) {
            super(P2PAgentServiceImpl.this, agenticScope);
            this.agents = agents;
            this.agentActivators = agents.stream().map(AgentActivator::new).toList();
        }

        @Override
        protected Object doAgentAction(DefaultAgenticScope agenticScope) {
            // First activation of agents that can run with the initial input
            for (AgentActivator agentActivator : agentActivators) {
                if (agentActivator.canActivate(agenticScope)) {
                    agentActivator.executeAsync(agenticScope);
                }
            }

            // Wait all agents to terminate
            agentActivators.forEach(AgentActivator::waitDone);

            // Start the shutdown process, marking it as terminating to prevent any further activations
            terminating = true;

            // Wait one last time for all agents to finish their current execution
            agentActivators.forEach(AgentActivator::waitDone);

            return result(agenticScope, output.apply(agenticScope));
        }

        private boolean terminating(AgenticScope agenticScope) {
            return terminating || invocationCounter.get() > maxAgentsInvocations || exitCondition.test(agenticScope, invocationCounter.get());
        }

        @Override
        protected InvocationHandler createSubAgentWithAgenticScope(DefaultAgenticScope agenticScope) {
            return new P2PInvocationHandler(agenticScope, agents);
        }

        private void notifyStateChanged(DefaultAgenticScope agenticScope, String state) {
            LOG.info("State '{}' changed to: {}", state, agenticScope.readState(state));
            agentActivators.forEach(a -> a.onStateChanged(agenticScope, state));
        }

        private class AgentActivator {
            private final AgentExecutor agentExecutor;
            private final String[] argumentNames;

            private volatile boolean shouldExecute = true;

            private CompletableFuture<Void> currentExecution;

            private AgentActivator(AgentExecutor agentExecutor) {
                this.agentExecutor = agentExecutor;
                this.argumentNames = agentExecutor.agentInvoker().argumentNames();
            }

            private boolean canActivate(AgenticScope agenticScope) {
                return Stream.of(argumentNames).allMatch(agenticScope::hasState);
            }

            private void executeAsync(DefaultAgenticScope agenticScope) {
                if (terminating(agenticScope) || !isDone()) {
                    return;
                }

                Runnable agentExecutorTask = () -> {
                    while (shouldExecute) {
                        shouldExecute = false;
                        LOG.info("Invoking agent: {}", agentExecutor.agentInvoker().uniqueName());
                        agentExecutor.syncExecute(agenticScope);
                        invocationCounter.incrementAndGet();
                        LOG.info("Invocation #{} terminated", invocationCounter.get());
                        if (terminating(agenticScope)) {
                            break;
                        }
                        notifyStateChanged(agenticScope, agentExecutor.agentInvoker().outputName());
                    }
                };

                this.currentExecution = CompletableFuture.runAsync(agentExecutorTask, exec);
            }

            private boolean isDone() {
                return currentExecution == null || currentExecution.isDone();
            }

            private void waitDone() {
                if (currentExecution != null) {
                    currentExecution.join();
                    currentExecution = null;
                }
            }

            private void onStateChanged(DefaultAgenticScope agenticScope, String state) {
                boolean inputChanged = Stream.of(argumentNames).anyMatch(argName -> argName.equals(state));
                // if the input changed, mark the agent to be executed again
                shouldExecute = shouldExecute || inputChanged;
                if (inputChanged && canActivate(agenticScope)) {
                    // if input changed and all inputs are available, execute the agent again if not already running
                    executeAsync(agenticScope);
                }
            }
        }
    }

    public static <T> P2PAgentService<T> builder(Class<T> agentServiceClass) {
        return new P2PAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public P2PAgentServiceImpl<T> subAgents(List<AgentExecutor> agentExecutors) {
        this.agents.addAll(agentExecutors);
        return this;
    }

    @Override
    public P2PAgentServiceImpl<T> maxAgentsInvocations(int maxAgentsInvocations) {
        this.maxAgentsInvocations = maxAgentsInvocations;
        return this;
    }

    @Override
    public P2PAgentServiceImpl<T> executor(Executor executor) {
        this.executor = executor;
        return this;
    }

    @Override
    public P2PAgentServiceImpl<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        this.exitCondition = (scope, invocationsCounter) -> exitCondition.test(scope);
        return this;
    }

    @Override
    public P2PAgentServiceImpl<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        this.exitCondition = exitCondition;
        return this;
    }
}
