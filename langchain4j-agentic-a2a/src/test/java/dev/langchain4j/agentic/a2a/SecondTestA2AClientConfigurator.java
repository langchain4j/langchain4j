package dev.langchain4j.agentic.a2a;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.ClientBuilder;

/**
 * Registered with the {@link java.util.ServiceLoader} for the tests, it delegates to the configurator a test sets,
 * and does nothing otherwise.
 */
public class SecondTestA2AClientConfigurator implements A2AClientConfigurator {

    static final AtomicReference<A2AClientConfigurator> DELEGATE = new AtomicReference<>();

    @Override
    public Map<String, String> agentCardHeaders(A2AClientContext context) {
        A2AClientConfigurator delegate = DELEGATE.get();
        return delegate == null ? Map.of() : delegate.agentCardHeaders(context);
    }

    @Override
    public Map<String, String> messageHeaders(A2AClientContext context) {
        A2AClientConfigurator delegate = DELEGATE.get();
        return delegate == null ? Map.of() : delegate.messageHeaders(context);
    }

    @Override
    public void customize(A2AClientContext context, ClientBuilder clientBuilder) {
        A2AClientConfigurator delegate = DELEGATE.get();
        if (delegate != null) {
            delegate.customize(context, clientBuilder);
        }
    }
}
