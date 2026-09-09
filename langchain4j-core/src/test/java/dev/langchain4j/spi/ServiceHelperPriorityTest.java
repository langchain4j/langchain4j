package dev.langchain4j.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two implementations of one service used to be resolved by whatever order the
 * {@link java.util.ServiceLoader} enumerated, which differs between a development run, a shaded jar
 * and a container image. An application or framework that supplies its own keeps it; a factory has
 * to ask to lose.
 */
class ServiceHelperPriorityTest {

    interface Greeter {
        String greet();
    }

    static class FrameworkGreeter implements Greeter {
        public String greet() {
            return "framework";
        }
    }

    static class YieldingGreeter implements Greeter, PrioritizedFactory {
        public String greet() {
            return "yielding";
        }

        public int priority() {
            return YIELDS_TO_OTHERS;
        }
    }

    @Test
    void a_yielding_factory_loses_whichever_order_it_arrives_in() {
        assertThat(ServiceHelper.sortByPriority(List.of(new YieldingGreeter(), new FrameworkGreeter()))
                        .get(0)
                        .greet())
                .isEqualTo("framework");

        assertThat(ServiceHelper.sortByPriority(List.of(new FrameworkGreeter(), new YieldingGreeter()))
                        .get(0)
                        .greet())
                .isEqualTo("framework");
    }

    @Test
    void a_yielding_factory_still_wins_when_it_is_the_only_one() {
        assertThat(ServiceHelper.sortByPriority(List.of(new YieldingGreeter()))
                        .get(0)
                        .greet())
                .isEqualTo("yielding");
    }

    @Test
    void equal_priorities_keep_the_order_they_arrived_in() {
        Greeter first = new FrameworkGreeter();
        assertThat(ServiceHelper.sortByPriority(List.of(first, new FrameworkGreeter())).get(0))
                .isSameAs(first);
    }
}
