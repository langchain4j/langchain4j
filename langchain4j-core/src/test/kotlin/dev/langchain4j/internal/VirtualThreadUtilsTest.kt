package dev.langchain4j.internal

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import org.mockito.kotlin.mock
import java.util.concurrent.ExecutorService

internal class VirtualThreadUtilsTest {
    /**
     * Verifies the behavior of the `createVirtualThreadExecutor` method from the `VirtualThreadUtils` class.
     *
     * This test ensures that the `createVirtualThreadExecutor` method correctly creates an
     * `ExecutorService` instance. When a fallback `ExecutorService` is provided through a lambda expression,
     * the method should return the same fallback instance if virtual threads are not available.
     *
     * The test is disabled for Java 21 or higher, as it specifically targets environments where virtual
     * threads are unsupported (Java versions up to 20). The method relies on mocked behavior to validate
     * the interaction and instance equivalence.
     */
    @Test
    @EnabledForJreRange(max = JRE.JAVA_20, disabledReason = "Requires Java 21 or higher")
    fun createPlatformThreadExecutor() {
        val expectedExecutor: ExecutorService = mock()
        val executorService = VirtualThreadUtils.createVirtualThreadExecutor({ expectedExecutor })
        executorService shouldBeSameInstanceAs expectedExecutor
    }

    /**
     * Tests the functionality of the `createVirtualThreadExecutor` method from the `VirtualThreadUtils` class.
     *
     * This test verifies that the `createVirtualThreadExecutor` method correctly returns an `ExecutorService`
     * that uses virtual threads if supported in the runtime environment. The created executor is tested
     * by submitting a task, ensuring that the task runs on a virtual thread.
     *
     * The test is enabled only for Java 21 or higher, as earlier versions do not support virtual threads.
     * It validates that the thread executing the submitted task starts with the prefix `VirtualThread`,
     * indicating the use of virtual threads.
     */
    @Test
    @EnabledForJreRange(min = JRE.JAVA_21, disabledReason = "Requires Java 20 or lower")
    fun createVirtualThreadExecutor() {
        val executorService = VirtualThreadUtils.createVirtualThreadExecutor({ null })
        executorService shouldNotBeNull {
            val task =
                submit {
                    val currentThread = Thread.currentThread()
                    currentThread.toString() shouldStartWith "VirtualThread["
                }
            task.get()
        }
    }
}
