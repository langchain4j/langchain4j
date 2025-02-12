package dev.langchain4j.store.embedding;

import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;

import java.time.Duration;

public final class TestUtils {
    private TestUtils() {
    }

    public static void awaitUntilAsserted(ThrowingRunnable assertion) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(assertion);
    }
}
