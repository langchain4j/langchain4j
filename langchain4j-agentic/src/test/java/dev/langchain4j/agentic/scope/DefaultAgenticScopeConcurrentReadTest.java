package dev.langchain4j.agentic.scope;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class DefaultAgenticScopeConcurrentReadTest {

    @Test
    void should_render_context_from_a_snapshot_during_concurrent_registration() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        ConcurrentAppend concurrentAppend = new ConcurrentAppend();
        Content image = () -> {
            concurrentAppend.pauseReader();
            return ContentType.IMAGE;
        };
        AgentInstance agent = new NamedAgent("agent", "agent-id");
        scope.registerContext(agent, NO_CHAT_MEMORY, UserMessage.from(image), "first answer");

        String conversation = concurrentAppend.readWhileAppending(
                () -> scope.contextAsConversation("agent"),
                () -> scope.registerContext(agent, NO_CHAT_MEMORY, UserMessage.from("follow-up"), "second answer"));

        assertThat(conversation).isEqualTo("""
                User: "[image]"
                agent agent: "first answer"
                """);
        assertThat(scope.contextAsConversation("agent")).isEqualTo(conversation + """
                User: "follow-up"
                agent agent: "second answer"
                """);
    }

    @Test
    void should_filter_invocations_by_name_from_a_snapshot_during_concurrent_registration() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        ConcurrentAppend concurrentAppend = new ConcurrentAppend();
        AgentInvocation first = new AgentInvocation(Object.class, "agent", "agent-id", Map.of(), null);
        scope.registerAgentInvocation(first);
        scope.registerAgentInvocation(new AgentInvocation(Object.class, "other", "other-id", Map.of(), null));
        pauseOnFirstElement(scope, concurrentAppend);
        AgentInvocation appended = new AgentInvocation(Object.class, "agent", "appended-id", Map.of(), null);

        List<AgentInvocation> invocations = concurrentAppend.readWhileAppending(
                () -> scope.agentInvocations("agent"), () -> scope.registerAgentInvocation(appended));

        assertThat(invocations).containsExactly(first);
        assertThat(scope.agentInvocations("agent")).containsExactly(first, appended);
    }

    @Test
    void should_filter_invocations_by_type_from_a_snapshot_during_concurrent_registration() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        ConcurrentAppend concurrentAppend = new ConcurrentAppend();
        AgentInvocation first = new AgentInvocation(Object.class, "agent", "agent-id", Map.of(), null);
        scope.registerAgentInvocation(first);
        scope.registerAgentInvocation(new AgentInvocation(String.class, "agent", "other-id", Map.of(), null));
        pauseOnFirstElement(scope, concurrentAppend);
        AgentInvocation appended = new AgentInvocation(Object.class, "agent", "appended-id", Map.of(), null);

        List<AgentInvocation> invocations = concurrentAppend.readWhileAppending(
                () -> scope.agentInvocations(Object.class), () -> scope.registerAgentInvocation(appended));

        assertThat(invocations).containsExactly(first);
        assertThat(scope.agentInvocations(Object.class)).containsExactly(first, appended);
    }

    /**
     * A real {@link AgentInvocation} has no callback the reader can wait in, so the scope's list is swapped for a
     * wrapper that still reads and writes that same list. The wrapper stops on the first element until the append has
     * landed: a fail-fast list then throws {@link java.util.ConcurrentModificationException}, and a
     * {@link java.util.concurrent.CopyOnWriteArrayList} keeps the elements it had when iteration started.
     */
    private static void pauseOnFirstElement(DefaultAgenticScope scope, ConcurrentAppend concurrentAppend)
            throws Exception {
        Field field = DefaultAgenticScope.class.getDeclaredField("agentInvocations");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AgentInvocation> invocations = (List<AgentInvocation>) field.get(scope);
        field.set(scope, new PausingList<>(invocations, concurrentAppend::pauseReader));
    }

    private static final ChatMemoryAccess NO_CHAT_MEMORY = new ChatMemoryAccess() {
        @Override
        public ChatMemory getChatMemory(Object memoryId) {
            return null;
        }

        @Override
        public boolean evictChatMemory(Object memoryId) {
            return false;
        }
    };

    private static final class NamedAgent implements AgentInstance {

        private final String name;
        private final String agentId;

        private NamedAgent(String name, String agentId) {
            this.name = name;
            this.agentId = agentId;
        }

        @Override
        public Class<?> type() {
            return Object.class;
        }

        @Override
        public Class<? extends Planner> plannerType() {
            return null;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String agentId() {
            return agentId;
        }

        @Override
        public String description() {
            return "";
        }

        @Override
        public Type outputType() {
            return String.class;
        }

        @Override
        public String outputKey() {
            return name;
        }

        @Override
        public boolean async() {
            return false;
        }

        @Override
        public List<AgentArgument> arguments() {
            return List.of();
        }

        @Override
        public AgentInstance parent() {
            return null;
        }

        @Override
        public List<AgentInstance> subagents() {
            return List.of();
        }

        @Override
        public AgenticSystemTopology topology() {
            return AgenticSystemTopology.AI_AGENT;
        }
    }

    /**
     * Delegates to the scope's real list, but waits inside the first {@link Iterator#next()} so a concurrent append
     * overlaps the iteration performed by {@code stream().filter(...)}.
     */
    private static final class PausingList<E> extends AbstractList<E> {

        private final List<E> delegate;
        private final Runnable pauseOnFirstElement;
        private boolean paused;

        private PausingList(List<E> delegate, Runnable pauseOnFirstElement) {
            this.delegate = delegate;
            this.pauseOnFirstElement = pauseOnFirstElement;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public E get(int index) {
            return delegate.get(index);
        }

        @Override
        public boolean add(E element) {
            return delegate.add(element);
        }

        @Override
        public Iterator<E> iterator() {
            Iterator<E> iterator = delegate.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public E next() {
                    E element = iterator.next();
                    if (!paused) {
                        paused = true;
                        pauseOnFirstElement.run();
                    }
                    return element;
                }

                @Override
                public void forEachRemaining(Consumer<? super E> action) {
                    while (hasNext()) {
                        action.accept(next());
                    }
                }
            };
        }
    }

    private static class ConcurrentAppend {

        private final CountDownLatch iterationStarted = new CountDownLatch(1);
        private final CountDownLatch appendCompleted = new CountDownLatch(1);

        void pauseReader() {
            iterationStarted.countDown();
            try {
                assertThat(appendCompleted.await(10, SECONDS))
                        .as("the writer appended while the reader was paused")
                        .isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the writer", e);
            }
        }

        <T> T readWhileAppending(Callable<T> read, Runnable append) throws Exception {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<T> reading = executor.submit(read);
                assertThat(iterationStarted.await(10, SECONDS))
                        .as("the reader started iterating")
                        .isTrue();
                executor.submit(() -> {
                            append.run();
                            appendCompleted.countDown();
                        })
                        .get(10, SECONDS);
                return reading.get(10, SECONDS);
            } finally {
                appendCompleted.countDown();
                executor.shutdownNow();
            }
        }
    }
}
