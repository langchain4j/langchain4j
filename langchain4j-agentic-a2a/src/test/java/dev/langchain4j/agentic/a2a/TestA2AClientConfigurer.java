package dev.langchain4j.agentic.a2a;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Registered with the {@link java.util.ServiceLoader} for the tests, it delegates to the configurer a test sets, and
 * does nothing otherwise.
 */
public class TestA2AClientConfigurer implements A2AClientConfigurer {

    static final AtomicReference<A2AClientConfigurer> DELEGATE = new AtomicReference<>();

    @Override
    public Map<String, String> agentCardHeaders(String a2aServerUrl) {
        A2AClientConfigurer delegate = DELEGATE.get();
        return delegate == null ? Map.of() : delegate.agentCardHeaders(a2aServerUrl);
    }

    @Override
    public void customize(String a2aServerUrl, AgentCard agentCard, ClientBuilder clientBuilder) {
        A2AClientConfigurer delegate = DELEGATE.get();
        if (delegate != null) {
            delegate.customize(a2aServerUrl, agentCard, clientBuilder);
        }
    }
}
