package dev.langchain4j.agentic.internal;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface A2AService {

    Logger LOG = LoggerFactory.getLogger(A2AService.class);

    <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass);

    default <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, String tenant, Class<T> agentServiceClass) {
        if (tenant != null && !tenant.isEmpty()) {
            LOG.warn(
                    "A2AService implementation {} does not support the tenant parameter; tenant '{}' will be ignored. Override a2aBuilder(url, tenant, class) to support multi-tenancy.",
                    getClass().getName(),
                    tenant);
        }
        return a2aBuilder(a2aServerUrl, agentServiceClass);
    }

    Optional<AgentExecutor> methodToAgentExecutor(InternalAgent a2aClient, Method method);

    static A2AService get() {
        return Provider.a2aService;
    }

    static void setA2AService(A2AService a2aService) {
        Provider.a2aService = a2aService;
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
        public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
            throw noA2AException();
        }

        @Override
        public Optional<AgentExecutor> methodToAgentExecutor(InternalAgent agent, Method method) {
            return Optional.empty();
        }

        private static UnsupportedOperationException noA2AException() {
            return new UnsupportedOperationException(
                    "No A2A service implementation found. Please add 'langchain4j-agentic-a2a' to your dependencies.");
        }
    }
}
