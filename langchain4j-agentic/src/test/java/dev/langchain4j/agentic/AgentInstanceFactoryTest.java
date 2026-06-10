package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AgentInstanceFactoryTest {

    static final ChatModel STUB_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("ok"))
                    .build();
        }
    };

    @Test
    void agentBuilder_uses_factory_when_set() {
        List<Class<?>> factoryCalls = new ArrayList<>();

        Function<InternalAgent, Object> factory = internalAgent -> {
            factoryCalls.add(internalAgent.type());
            return Proxy.newProxyInstance(
                    internalAgent.type().getClassLoader(),
                    AgentBuilder.interfacesToImplement(internalAgent.type()),
                    (InvocationHandler) internalAgent);
        };

        SimpleAgent agent = AgenticServices.agentBuilder(SimpleAgent.class)
                .chatModel(STUB_MODEL)
                .agentInstanceFactory(factory)
                .build();

        assertThat(agent).isNotNull();
        assertThat(factoryCalls).containsExactly(SimpleAgent.class);
    }

    @Test
    void agentBuilder_falls_back_to_proxy_when_factory_is_null() {
        SimpleAgent agent = AgenticServices.agentBuilder(SimpleAgent.class)
                .chatModel(STUB_MODEL)
                .build();

        assertThat(agent).isNotNull();
        assertThat(Proxy.isProxyClass(agent.getClass())).isTrue();
    }

    @Test
    void agentConfigurator_propagates_factory_to_leaf_agent() {
        List<Class<?>> factoryCalls = new ArrayList<>();

        Function<InternalAgent, Object> factory = internalAgent -> {
            factoryCalls.add(internalAgent.type());
            return Proxy.newProxyInstance(
                    internalAgent.type().getClassLoader(),
                    AgentBuilder.interfacesToImplement(internalAgent.type()),
                    (InvocationHandler) internalAgent);
        };

        var configurator = new AgentConfigurator(ctx -> {}, null, factory);

        SimpleAgentWithModel agent = AgenticServices.createAgenticSystem(
                SimpleAgentWithModel.class, configurator);

        assertThat(agent).isNotNull();
        assertThat(factoryCalls).contains(SimpleAgentWithModel.class);
    }

    @Test
    void agentConfigurator_propagates_factory_to_sequence_builder() {
        List<Class<?>> factoryCalls = new ArrayList<>();

        Function<InternalAgent, Object> factory = internalAgent -> {
            factoryCalls.add(internalAgent.type());
            return Proxy.newProxyInstance(
                    internalAgent.type().getClassLoader(),
                    AgentBuilder.interfacesToImplement(internalAgent.type()),
                    (InvocationHandler) internalAgent);
        };

        var configurator = new AgentConfigurator(ctx -> {}, null, factory);

        SequenceAgentService agent = AgenticServices.createAgenticSystem(
                SequenceAgentService.class, STUB_MODEL, configurator);

        assertThat(agent).isNotNull();
        assertThat(factoryCalls).contains(SequenceAgentService.class);
    }

    public interface SimpleAgent {

        @UserMessage("Say {{message}}")
        @Agent(outputKey = "response")
        String chat(@V("message") String message);
    }

    public interface SimpleAgentWithModel {

        @UserMessage("Say {{message}}")
        @Agent(outputKey = "response")
        String chat(@V("message") String message);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return STUB_MODEL;
        }
    }

    public interface SubAgentA {

        @UserMessage("Process {{message}}")
        @Agent(description = "SubAgent A", outputKey = "response")
        String process(@V("message") String message);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return STUB_MODEL;
        }
    }

    public interface SequenceAgentService {

        @SequenceAgent(subAgents = {SubAgentA.class})
        String run(@V("message") String message);
    }
}
