package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.ChatSupplierParameterResolver;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatSupplierParameterResolverTest {

    @BeforeEach
    @AfterEach
    void clearState() {
        DeclarativeUtil.getChatSupplierParameterResolvers().clear();
        capturedService = null;
        capturedAnotherService = null;
        capturedScopeValue = null;
        supportsCalls.clear();
        resolveCalls.clear();
    }

    static final ChatModel DUMMY_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("test-response"))
                    .build();
        }
    };

    static class TestService {
        private final String value;

        TestService(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    static class AnotherService {
        private final int id;

        AnotherService(int id) {
            this.id = id;
        }

        int getId() {
            return id;
        }
    }

    static TestService capturedService;
    static AnotherService capturedAnotherService;
    static String capturedScopeValue;
    static final List<ChatSupplierParameterResolver.Context> supportsCalls = new ArrayList<>();
    static final List<ChatSupplierParameterResolver.Context> resolveCalls = new ArrayList<>();

    // -- Sub-agents with parameterized @ChatModelSupplier --

    public interface EchoAgent {

        @UserMessage("Say {{it}}")
        @Agent(description = "An echo agent", outputKey = "echo")
        String say(@V("it") String input);

        @ChatModelSupplier
        static ChatModel chatModel(@V("service") TestService service) {
            capturedService = service;
            return DUMMY_MODEL;
        }
    }

    public interface EchoAgentWithTwoParams {

        @UserMessage("Say {{it}}")
        @Agent(description = "An echo agent", outputKey = "echo")
        String say(@V("it") String input);

        @ChatModelSupplier
        static ChatModel chatModel(@V("service") TestService service, @V("another") AnotherService another) {
            capturedService = service;
            capturedAnotherService = another;
            return DUMMY_MODEL;
        }
    }

    public interface EchoAgentWithMixedParams {

        @UserMessage("Say {{it}}")
        @Agent(description = "An echo agent", outputKey = "echo")
        String say(@V("it") String input);

        @ChatModelSupplier
        static ChatModel chatModel(@V("it") String fromScope, @V("service") TestService fromResolver) {
            capturedScopeValue = fromScope;
            capturedService = fromResolver;
            return DUMMY_MODEL;
        }
    }

    // -- Wrapper agents to provide agentic context --

    public interface WrapperAgent {
        @SequenceAgent(outputKey = "echo", subAgents = {EchoAgent.class})
        String run(@V("it") String input);
    }

    public interface WrapperAgentForTwo {
        @SequenceAgent(outputKey = "echo", subAgents = {EchoAgentWithTwoParams.class})
        String run(@V("it") String input);
    }

    public interface WrapperAgentForMixed {
        @SequenceAgent(outputKey = "echo", subAgents = {EchoAgentWithMixedParams.class})
        String run(@V("it") String input);
    }

    @Test
    void resolver_provides_value_and_context_is_correct() {
        TestService testService = new TestService("resolved-value");

        DeclarativeUtil.addChatSupplierParameterResolver(new ChatSupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                supportsCalls.add(context);
                return context.parameter().getType() == TestService.class;
            }

            @Override
            public Object resolve(Context context) {
                resolveCalls.add(context);
                return testService;
            }
        });

        WrapperAgent agent = AgenticServices.createAgenticSystem(WrapperAgent.class, DUMMY_MODEL);

        // supports() called during agent creation for the EchoAgent's @ChatModelSupplier parameter
        assertThat(supportsCalls).isNotEmpty();
        ChatSupplierParameterResolver.Context ctx = supportsCalls.stream()
                .filter(c -> c.parameter().getType() == TestService.class)
                .findFirst().orElseThrow();
        assertThat(ctx.declaringAgentClass()).isEqualTo(EchoAgent.class);
        assertThat(ctx.supplierMethod().getName()).isEqualTo("chatModel");
        assertThat(ctx.parameter().getType()).isEqualTo(TestService.class);

        // resolve() called during agent invocation
        String result = agent.run("hello");

        assertThat(resolveCalls).isNotEmpty();
        assertThat(resolveCalls.stream().anyMatch(c -> c.parameter().getType() == TestService.class)).isTrue();
        assertThat(capturedService).isSameAs(testService);
        assertThat(result).isEqualTo("test-response");
    }

    @Test
    void multiple_resolvers_each_handles_its_type() {
        TestService testService = new TestService("first");
        AnotherService anotherService = new AnotherService(42);

        DeclarativeUtil.addChatSupplierParameterResolver(new ChatSupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == TestService.class;
            }

            @Override
            public Object resolve(Context context) {
                return testService;
            }
        });

        DeclarativeUtil.addChatSupplierParameterResolver(new ChatSupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == AnotherService.class;
            }

            @Override
            public Object resolve(Context context) {
                return anotherService;
            }
        });

        WrapperAgentForTwo agent = AgenticServices.createAgenticSystem(WrapperAgentForTwo.class, DUMMY_MODEL);
        agent.run("hello");

        assertThat(capturedService).isSameAs(testService);
        assertThat(capturedAnotherService).isSameAs(anotherService);
    }

    @Test
    void first_matching_resolver_wins() {
        TestService fromFirst = new TestService("first-resolver");
        TestService fromSecond = new TestService("second-resolver");

        DeclarativeUtil.addChatSupplierParameterResolver(new ChatSupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == TestService.class;
            }

            @Override
            public Object resolve(Context context) {
                return fromFirst;
            }
        });

        DeclarativeUtil.addChatSupplierParameterResolver(new ChatSupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == TestService.class;
            }

            @Override
            public Object resolve(Context context) {
                return fromSecond;
            }
        });

        WrapperAgent agent = AgenticServices.createAgenticSystem(WrapperAgent.class, DUMMY_MODEL);
        agent.run("hello");

        assertThat(capturedService).isSameAs(fromFirst);
    }

    @Test
    void resolver_and_scope_params_coexist() {
        TestService testService = new TestService("injected");

        DeclarativeUtil.addChatSupplierParameterResolver(new ChatSupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == TestService.class;
            }

            @Override
            public Object resolve(Context context) {
                return testService;
            }
        });

        WrapperAgentForMixed agent = AgenticServices.createAgenticSystem(WrapperAgentForMixed.class, DUMMY_MODEL);
        agent.run("hello-from-scope");

        assertThat(capturedScopeValue).isEqualTo("hello-from-scope");
        assertThat(capturedService).isSameAs(testService);
    }
}
