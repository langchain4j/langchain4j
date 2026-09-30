package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method that customizes the underlying A2A SDK
 * {@code ClientBuilder} before the {@code Client} is constructed.
 * <p>
 * The annotated method must be static, take a single parameter of type
 * {@code org.a2aproject.sdk.client.ClientBuilder}, and return {@code void}.
 * It is invoked once at build time, allowing transport, interceptors, streaming,
 * and other low-level configuration to be specified declaratively.
 * <p>
 * Example:
 * <pre>{@code
 * public interface MyA2AAgent {
 *
 *     @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "result")
 *     String invoke(@V("input") String input);
 *
 *     @A2AClientCustomizer
 *     static void customizer(ClientBuilder cb) {
 *         cb.withTransport(JSONRPCTransport.class,
 *             new JSONRPCTransportConfigBuilder()
 *                 .addInterceptor(new MyOtelInterceptor()));
 *     }
 * }
 * }</pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface A2AClientCustomizer {
}
