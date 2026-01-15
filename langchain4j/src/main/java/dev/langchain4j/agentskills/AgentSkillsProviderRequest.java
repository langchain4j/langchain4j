package dev.langchain4j.agentskills;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;

/**
 * Request object for {@link AgentSkillsProvider#provideSkills(AgentSkillsProviderRequest)}.
 * <p>
 * Contains context information that can be used by the provider to determine
 * which skills to provide.
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public class AgentSkillsProviderRequest {

    private final InvocationContext invocationContext;
    private final UserMessage userMessage;

    private AgentSkillsProviderRequest(Builder builder) {
        this.invocationContext = builder.invocationContext;
        this.userMessage = builder.userMessage;
    }

    /**
     * Returns the invocation context.
     *
     * @return the invocation context, or null
     */
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * Returns the user message.
     *
     * @return the user message, or null
     */
    public UserMessage userMessage() {
        return userMessage;
    }

    /**
     * Returns the chat memory ID from the invocation context.
     *
     * @return the chat memory ID, or null
     */
    public Object chatMemoryId() {
        return invocationContext != null ? invocationContext.chatMemoryId() : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private InvocationContext invocationContext;
        private UserMessage userMessage;

        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        public Builder userMessage(UserMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public AgentSkillsProviderRequest build() {
            return new AgentSkillsProviderRequest(this);
        }
    }
}
