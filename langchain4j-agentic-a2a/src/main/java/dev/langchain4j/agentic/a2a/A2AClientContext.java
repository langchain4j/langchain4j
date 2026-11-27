package dev.langchain4j.agentic.a2a;

import org.a2aproject.sdk.spec.AgentCard;

/**
 * The A2A client agent an {@link A2AClientConfigurator} is applied to.
 * <p>
 * New properties may be added in later versions; use {@link #builder()} to create one, for example in tests.
 */
public final class A2AClientContext {

    private final String a2aServerUrl;
    private final String tenant;
    private final String agentName;
    private final Class<?> agentInterface;
    private final AgentCard agentCard;

    private A2AClientContext(Builder builder) {
        this.a2aServerUrl = builder.a2aServerUrl;
        this.tenant = builder.tenant;
        this.agentName = builder.agentName;
        this.agentInterface = builder.agentInterface;
        this.agentCard = builder.agentCard;
    }

    /**
     * @return the URL the agent was built with
     */
    public String a2aServerUrl() {
        return a2aServerUrl;
    }

    /**
     * @return the tenant of the request: the tenant of the call for messages, otherwise the agent's configured
     *     tenant; {@code null} when there is none
     */
    public String tenant() {
        return tenant;
    }

    /**
     * @return the name of the local agent
     */
    public String agentName() {
        return agentName;
    }

    /**
     * @return the interface the agent was built from
     */
    public Class<?> agentInterface() {
        return agentInterface;
    }

    /**
     * @return the remote agent's Agent Card, or {@code null} while it is being fetched
     */
    public AgentCard agentCard() {
        return agentCard;
    }

    @Override
    public String toString() {
        return "A2AClientContext{a2aServerUrl=" + a2aServerUrl + ", tenant=" + tenant + ", agentName=" + agentName
                + ", agentInterface=" + (agentInterface == null ? null : agentInterface.getName()) + ", agentCard="
                + (agentCard == null ? null : agentCard.name()) + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String a2aServerUrl;
        private String tenant;
        private String agentName;
        private Class<?> agentInterface;
        private AgentCard agentCard;

        private Builder() {}

        public Builder a2aServerUrl(String a2aServerUrl) {
            this.a2aServerUrl = a2aServerUrl;
            return this;
        }

        public Builder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder agentInterface(Class<?> agentInterface) {
            this.agentInterface = agentInterface;
            return this;
        }

        public Builder agentCard(AgentCard agentCard) {
            this.agentCard = agentCard;
            return this;
        }

        public A2AClientContext build() {
            return new A2AClientContext(this);
        }
    }
}
