package dev.langchain4j.agentic.declarative;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a static method that supplies the transport configuration for a declarative {@link A2AClientAgent}.
 * <p>
 * The annotated method must be static, take no arguments and return an instance of
 * {@code dev.langchain4j.agentic.a2a.A2AClientTransportConfigurer} (provided by the {@code langchain4j-agentic-a2a}
 * module). It allows the underlying A2A client transport to be selected and customized, for example to register call
 * interceptors adding authentication or OpenTelemetry context propagation.
 * <pre>{@code
 * public interface MyA2AAgent {
 *
 *     @A2AClientAgent(a2aServerUrl = "http://localhost:8080", outputKey = "response")
 *     String ask(@V("question") String question);
 *
 *     @A2AClientTransportSupplier
 *     static A2AClientTransportConfigurer transport() {
 *         return A2AClientTransportConfigurer.transport(
 *                 JSONRPCTransport.class,
 *                 new JSONRPCTransportConfigBuilder().addInterceptor(myOpenTelemetryInterceptor));
 *     }
 * }
 * }</pre>
 */
@Retention(RUNTIME)
@Target({METHOD})
public @interface A2AClientTransportSupplier {}
