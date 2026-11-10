/**
 * Model mocks for tests: they need no network, answer the way a test tells them to, and record the requests they
 * receive. Add {@code dev.langchain4j:langchain4j-test} with test scope to use them.
 * <p>
 * A mock remembers which of its steps were taken and which requests it received, so create a new one for each test.
 * Do not share a mock with steps between tests, for example as a bean in a cached Spring application context. A mock
 * that computes its answers from the request, such as {@code ChatModelMock.thatResponds(request -> answer)}, can be
 * shared.
 */
package dev.langchain4j.test.mock;
