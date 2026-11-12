/**
 * Model mocks for tests: they need no network, answer the way a test tells them to, and record the requests they
 * receive. Add {@code dev.langchain4j:langchain4j-test} with test scope to use them.
 * <p>
 * {@link MockChatModel}, {@link MockStreamingChatModel} and {@link MockDecisionModel} follow a script: every
 * {@code that...} method creates a mock with its first step (one step per message for the message-list forms), every
 * {@code then...} method adds a step, each step answers one request, a request after the last step fails, and
 * {@code thenRepeats()} at the end of the script answers every further request with the last step. A step can also
 * compute its answer from the request, for example {@code MockChatModel.thatResponds(request -> answer)}.
 * The embedding, scoring and moderation mocks have no script, like the real models they replace, which give the same
 * answer to the same input: they answer every request the same way, and their {@code thatFails(...)} fails every
 * request.
 * <p>
 * A mock remembers which steps were taken and which requests it received, so create a new one for each test, for
 * example by replacing the model bean for each test. In Quarkus, {@code QuarkusMock.installMockForType(...)} does that.
 * In Spring, {@code @TestBean} replaces the bean once per cached Spring context, which is usually per test class; for a
 * new mock per test, build the context in the test, for example with {@code ApplicationContextRunner}. Give the
 * {@code @TestBean} field the type of the bean it replaces, for example {@code ChatModel}: for a field of the mock's
 * type, Spring finds no bean to replace and adds the mock as a second model bean. A script that
 * ends with {@code thenRepeats()}, or a mock without a script, can be shared between tests, but its {@code requests()}
 * then hold the requests of every test that used it. A script without {@code thenRepeats()} that is shared between
 * tests fails as soon as it runs out. A mock keeps every request it receives, so it is not meant for load tests or
 * other long runs.
 * <p>
 * A mock can be called from several threads at once. The steps of a script are taken in the order the requests arrive,
 * so to answer concurrent requests, compute the answer from the request, for example
 * {@code MockChatModel.thatResponds(request -> answer).thenRepeats()}. Set the listeners with {@code withListeners(...)}
 * before the first request.
 */
package dev.langchain4j.test.mock;
