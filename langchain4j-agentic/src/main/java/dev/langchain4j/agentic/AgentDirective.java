package dev.langchain4j.agentic;

public abstract sealed class AgentDirective {

    public static AgentDirective terminate() {
        return Terminate.INSTANCE;
    }

    public static AgentDirective prompt() {
        return Prompt.INSTANCE;
    }

    public static AgentDirective redirectTo(String agentName) {
        return new Redirect(agentName);
    }

    static final class Terminate extends AgentDirective {
        static final Terminate INSTANCE = new Terminate();
    }

    static final class Prompt extends AgentDirective {
        static final Prompt INSTANCE = new Prompt();
    }

    static final class Redirect extends AgentDirective {
        private final String agentName;

        Redirect(String agentName) {
            this.agentName = agentName;
        }

        public String agentName() {
            return agentName;
        }
    }
}
