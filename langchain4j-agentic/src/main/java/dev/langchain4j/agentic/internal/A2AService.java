package dev.langchain4j.agentic.internal;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ServiceLoader;

public interface A2AService {

    boolean isPresent();

    <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass);

    Optional<AgentExecutor> methodToAgentExecutor(AgentSpecification a2aClient, Method method);

    static A2AService get() {
        return Provider.a2aService;
    }

    class Provider {

        static A2AService a2aService = loadA2AService();

        private Provider() { }

        private static A2AService loadA2AService() {
            ServiceLoader<A2AService> loader =
                    ServiceLoader.load(A2AService.class);

            for (A2AService service : loader) {
                return service;
            }
            return new DummyA2AService();
        }
    }

    class DummyA2AService implements A2AService {

        private DummyA2AService() { }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
            throw noA2AException();
        }

        @Override
        public Optional<AgentExecutor> methodToAgentExecutor(AgentSpecification agent, Method method) {
            throw noA2AException();
        }

        private static UnsupportedOperationException noA2AException() {
            return new UnsupportedOperationException(
                    "No A2A service implementation found. Please add 'langchain4j-agentic-a2a' to your dependencies.");
        }
    }
}
