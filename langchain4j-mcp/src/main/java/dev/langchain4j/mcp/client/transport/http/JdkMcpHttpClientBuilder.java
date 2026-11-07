package dev.langchain4j.mcp.client.transport.http;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;

/**
 * Compatibility bridge for the transport's deprecated JDK-only settings and demand-driven streaming.
 * Uses reflection so the JDK backend remains a runtime dependency that applications can exclude.
 */
final class JdkMcpHttpClientBuilder {

    private JdkMcpHttpClientBuilder() {}

    private static Class<?> builderType() {
        try {
            return Class.forName("dev.langchain4j.http.client.jdk.JdkHttpClientBuilder");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    static boolean supports(HttpClientBuilder builder) {
        Class<?> type = builderType();
        return type != null && type.isInstance(builder);
    }

    static void configure(
            HttpClientBuilder builder,
            SSLContext sslContext,
            boolean forceHttp1,
            Boolean followRedirects,
            Executor executor) {
        try {
            Class<?> type = builderType();
            java.net.http.HttpClient.Builder nativeBuilder = (java.net.http.HttpClient.Builder)
                    type.getMethod("httpClientBuilder").invoke(builder);
            if (nativeBuilder == null) {
                nativeBuilder = java.net.http.HttpClient.newBuilder().version(java.net.http.HttpClient.Version.HTTP_2);
            }
            if (forceHttp1) {
                nativeBuilder.version(java.net.http.HttpClient.Version.HTTP_1_1);
            }
            if (followRedirects != null) {
                nativeBuilder.followRedirects(
                        followRedirects
                                ? java.net.http.HttpClient.Redirect.NORMAL
                                : java.net.http.HttpClient.Redirect.NEVER);
            }
            if (executor != null) {
                nativeBuilder.executor(executor);
            }
            if (sslContext != null) {
                nativeBuilder.sslContext(sslContext);
            }
            type.getMethod("httpClientBuilder", java.net.http.HttpClient.Builder.class)
                    .invoke(builder, nativeBuilder);
            type.getMethod("streamingBackpressure", boolean.class).invoke(builder, true);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Cannot configure the JDK HTTP client for MCP", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot configure the JDK HTTP client for MCP", e);
        }
    }
}
