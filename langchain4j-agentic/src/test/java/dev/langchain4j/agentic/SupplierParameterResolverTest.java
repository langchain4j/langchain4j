package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ContentRetrieverSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.agentic.declarative.ParallelExecutor;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplierParameterResolverTest {

    @BeforeEach
    @AfterEach
    void clearState() {
        DeclarativeUtil.getSupplierParameterResolvers().clear();
        capturedRetriever = null;
        capturedMemory = null;
        capturedParallelExecutorHolder = null;
        capturedParallelMapperExecutorHolder = null;
    }

    static final ChatModel DUMMY_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("test-response"))
                    .build();
        }
    };

    static class RetrieverService {
        ContentRetriever create() {
            return query -> List.of(Content.from(TextSegment.from("resolved-content")));
        }
    }

    static class MemoryService {
        ChatMemory create() {
            return new ChatMemory() {
                private final List<ChatMessage> messages = new ArrayList<>();

                @Override
                public Object id() {
                    return "resolved";
                }

                @Override
                public void add(ChatMessage message) {
                    messages.add(message);
                }

                @Override
                public List<ChatMessage> messages() {
                    return new ArrayList<>(messages);
                }

                @Override
                public void clear() {
                    messages.clear();
                }
            };
        }
    }

    static RetrieverService capturedRetriever;
    static MemoryService capturedMemory;
    static ExecutorHolder capturedParallelExecutorHolder;
    static ExecutorHolder capturedParallelMapperExecutorHolder;

    static class ExecutorHolder {
        private final CountingExecutor executor = new CountingExecutor();

        Executor executor() {
            return executor;
        }

        int executionCount() {
            return executor.executionCount();
        }
    }

    static class CountingExecutor implements Executor {
        private final AtomicInteger executionCount = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            executionCount.incrementAndGet();
            command.run();
        }

        int executionCount() {
            return executionCount.get();
        }
    }

    // -- Agents with parameterised non-chat suppliers --

    public interface RetrieverAgent {

        @UserMessage("Find: {{it}}")
        @Agent(description = "Agent with resolved retriever", outputKey = "result")
        String find(@V("it") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return DUMMY_MODEL;
        }

        @ContentRetrieverSupplier
        static ContentRetriever retriever(@V("svc") RetrieverService service) {
            capturedRetriever = service;
            return service.create();
        }
    }

    public interface MemoryAgent {

        @UserMessage("Remember: {{it}}")
        @Agent(description = "Agent with resolved memory", outputKey = "result")
        String remember(@V("it") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return DUMMY_MODEL;
        }

        @ChatMemorySupplier
        static ChatMemory memory(@V("svc") MemoryService service) {
            capturedMemory = service;
            return service.create();
        }
    }

    public interface RetrieverWrapper {
        @SequenceAgent(
                outputKey = "result",
                subAgents = {RetrieverAgent.class})
        String run(@V("it") String input);
    }

    public interface MemoryWrapper {
        @SequenceAgent(
                outputKey = "result",
                subAgents = {MemoryAgent.class})
        String run(@V("it") String input);
    }

    public interface FirstParallelAgent {

        @UserMessage("First: {{it}}")
        @Agent(description = "First parallel agent", outputKey = "first")
        String first(@V("it") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return DUMMY_MODEL;
        }
    }

    public interface SecondParallelAgent {

        @UserMessage("Second: {{it}}")
        @Agent(description = "Second parallel agent", outputKey = "second")
        String second(@V("it") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return DUMMY_MODEL;
        }
    }

    public interface ParallelAgentWithResolvedExecutor {

        @ParallelAgent(
                outputKey = "result",
                subAgents = {FirstParallelAgent.class, SecondParallelAgent.class})
        String run(@V("it") String input);

        @ParallelExecutor
        static Executor executor(ExecutorHolder holder) {
            capturedParallelExecutorHolder = holder;
            return holder.executor();
        }

        @Output
        static String output(@V("first") String first, @V("second") String second) {
            return first + ":" + second;
        }
    }

    public interface ItemAgent {

        @UserMessage("Map: {{item}}")
        @Agent(description = "Map item", outputKey = "mapped")
        String map(@V("item") String item);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return DUMMY_MODEL;
        }
    }

    public interface ParallelMapperAgentWithResolvedExecutor {

        @ParallelMapperAgent(subAgent = ItemAgent.class, outputKey = "mappedItems")
        List<String> map(@V("items") List<String> items);

        @ParallelExecutor
        static Executor executor(ExecutorHolder holder) {
            capturedParallelMapperExecutorHolder = holder;
            return holder.executor();
        }
    }

    @Test
    void contentRetrieverSupplier_parameter_resolved() {
        RetrieverService service = new RetrieverService();

        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == RetrieverService.class;
            }

            @Override
            public Object resolve(Context context) {
                return service;
            }
        });

        RetrieverWrapper agent = AgenticServices.createAgenticSystem(RetrieverWrapper.class, DUMMY_MODEL);
        String result = agent.run("test");

        assertThat(capturedRetriever).isSameAs(service);
        assertThat(result).isEqualTo("test-response");
    }

    @Test
    void chatMemorySupplier_parameter_resolved() {
        MemoryService service = new MemoryService();

        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == MemoryService.class;
            }

            @Override
            public Object resolve(Context context) {
                return service;
            }
        });

        MemoryWrapper agent = AgenticServices.createAgenticSystem(MemoryWrapper.class, DUMMY_MODEL);
        String result = agent.run("test");

        assertThat(capturedMemory).isSameAs(service);
        assertThat(result).isEqualTo("test-response");
    }

    @Test
    void resolver_context_reports_correct_supplier_method_for_non_chat() {
        List<SupplierParameterResolver.Context> capturedContexts = new ArrayList<>();

        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                capturedContexts.add(context);
                return context.parameter().getType() == RetrieverService.class;
            }

            @Override
            public Object resolve(Context context) {
                return new RetrieverService();
            }
        });

        AgenticServices.createAgenticSystem(RetrieverWrapper.class, DUMMY_MODEL);

        SupplierParameterResolver.Context ctx = capturedContexts.stream()
                .filter(c -> c.parameter().getType() == RetrieverService.class)
                .findFirst()
                .orElseThrow();
        assertThat(ctx.declaringAgentClass()).isEqualTo(RetrieverAgent.class);
        assertThat(ctx.supplierMethod().getName()).isEqualTo("retriever");
    }

    @Test
    void parallelExecutorSupplier_parameter_resolved() {
        ExecutorHolder holder = new ExecutorHolder();
        registerExecutorHolderResolver(holder);

        ParallelAgentWithResolvedExecutor agent =
                AgenticServices.createAgenticSystem(ParallelAgentWithResolvedExecutor.class, DUMMY_MODEL);
        String result = agent.run("test");

        assertThat(capturedParallelExecutorHolder).isSameAs(holder);
        assertThat(holder.executionCount()).isEqualTo(2);
        assertThat(result).isEqualTo("test-response:test-response");
    }

    @Test
    void parallelExecutorSupplier_parameter_withoutResolver_failsFast() {
        assertThatThrownBy(
                        () -> AgenticServices.createAgenticSystem(ParallelAgentWithResolvedExecutor.class, DUMMY_MODEL))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("@ParallelExecutor")
                .hasMessageContaining("SupplierParameterResolver");
    }

    @Test
    void parallelExecutorSupplier_parameter_withUnrelatedResolver_failsFast() {
        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == RetrieverService.class;
            }

            @Override
            public Object resolve(Context context) {
                return new RetrieverService();
            }
        });

        assertThatThrownBy(
                        () -> AgenticServices.createAgenticSystem(ParallelAgentWithResolvedExecutor.class, DUMMY_MODEL))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("ExecutorHolder")
                .hasMessageContaining("@ParallelExecutor")
                .hasMessageContaining("SupplierParameterResolver");
    }

    @Test
    void parallelMapperExecutorSupplier_parameter_resolved() {
        ExecutorHolder holder = new ExecutorHolder();
        registerExecutorHolderResolver(holder);

        ParallelMapperAgentWithResolvedExecutor agent =
                AgenticServices.createAgenticSystem(ParallelMapperAgentWithResolvedExecutor.class, DUMMY_MODEL);
        List<String> result = agent.map(List.of("first", "second"));

        assertThat(capturedParallelMapperExecutorHolder).isSameAs(holder);
        assertThat(holder.executionCount()).isEqualTo(2);
        assertThat(result).containsExactly("test-response", "test-response");
    }

    private static void registerExecutorHolderResolver(ExecutorHolder holder) {
        DeclarativeUtil.addSupplierParameterResolver(new SupplierParameterResolver() {
            @Override
            public boolean supports(Context context) {
                return context.parameter().getType() == ExecutorHolder.class;
            }

            @Override
            public Object resolve(Context context) {
                return holder;
            }
        });
    }
}
