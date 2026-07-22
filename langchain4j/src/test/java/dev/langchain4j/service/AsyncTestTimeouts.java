package dev.langchain4j.service;

/**
 * Shared timeout ceiling for the non-blocking / async AiService tests. These tests drive stub models and finish in
 * milliseconds, so the timeouts are "don't hang forever" safety nets, not latency assertions. The ceiling is kept
 * deliberately generous so that CPU saturation during a full parallel test run (many tests sharing the JVM-wide
 * virtual-thread executor) cannot fail a healthy test by narrowly missing a tight deadline.
 */
final class AsyncTestTimeouts {

    private AsyncTestTimeouts() {}

    static final long TIMEOUT_SECONDS = 30;
}
