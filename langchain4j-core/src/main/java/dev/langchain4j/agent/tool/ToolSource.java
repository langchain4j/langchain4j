package dev.langchain4j.agent.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Describes where a tool comes from.
 * <p>
 * A {@code ToolSource} is <b>trusted</b> information: it is set by the framework or by the application
 * when the tool is registered, and it is <b>never</b> derived from remote data such as MCP {@code _meta},
 * {@code openWorldHint} or {@link ToolSpecification#metadata()}. Guardrails can therefore rely on it
 * to make security decisions.
 * <p>
 * The possible origins are:
 * <ul>
 *     <li>{@link Local}: a local method annotated with {@code @Tool}</li>
 *     <li>{@link Mcp}: an MCP server, or several MCP servers (identified by their keys)</li>
 *     <li>{@link Programmatic}: a tool registered programmatically, without an explicit source</li>
 * </ul>
 *
 * @since 1.24.0
 */
@Experimental
public sealed interface ToolSource permits ToolSource.Local, ToolSource.Mcp, ToolSource.Programmatic {

    /**
     * Creates a source for a local {@code @Tool} method. Only the declaring class name, the method name and the
     * parameter type names are extracted; the {@link Method} itself is not retained. The declaring class name uses
     * the format returned by {@link Class#getName()} (a binary name, for example {@code com.acme.Outer$Inner});
     * parameter type names use the format returned by {@link Class#getTypeName()} (for example {@code int},
     * {@code java.lang.String[]}, or {@code com.acme.Outer$Inner[]}). These formats allow equality with a source
     * created by {@link #local(String, String, List)}.
     *
     * @param method the tool method, must not be {@code null}.
     * @return the source.
     */
    static ToolSource local(Method method) {
        ensureNotNull(method, "method");
        return new LocalToolSource(
                method.getDeclaringClass().getName(),
                method.getName(),
                Arrays.stream(method.getParameterTypes())
                        .map(Class::getTypeName)
                        .toList());
    }

    /**
     * Creates a source for a local {@code @Tool} method from its descriptive data, without reflection. To compare
     * equal to a source created by {@link #local(Method)}, {@code declaringClassName} must use the format returned
     * by {@link Class#getName()} (a binary name), and each parameter type name must use the format returned by
     * {@link Class#getTypeName()} (including binary names for nested classes and a {@code []} suffix for arrays).
     *
     * @param declaringClassName the class name as returned by {@link Class#getName()}, must not be blank.
     * @param methodName         the method name, must not be blank.
     * @param parameterTypeNames the parameter type names as returned by {@link Class#getTypeName()}, in declaration
     *                           order (distinguishes overloads); must not be null or contain blank elements. May be
     *                           empty.
     * @return the source.
     */
    static ToolSource local(String declaringClassName, String methodName, List<String> parameterTypeNames) {
        ensureNotNull(parameterTypeNames, "parameterTypeNames");
        for (String name : parameterTypeNames) {
            ensureNotBlank(name, "parameterTypeName");
        }
        return new LocalToolSource(
                ensureNotBlank(declaringClassName, "declaringClassName"),
                ensureNotBlank(methodName, "methodName"),
                parameterTypeNames);
    }

    /**
     * Creates a source for a single MCP server.
     *
     * @param serverKey the key identifying the MCP server, must not be blank.
     * @return the source.
     */
    static ToolSource mcp(String serverKey) {
        return new McpToolSource(Set.of(ensureNotBlank(serverKey, "serverKey")));
    }

    /**
     * Creates a source spanning several MCP servers, for example tools that list or read resources
     * across multiple MCP clients. Duplicate keys are collapsed; the iteration order of
     * {@link Mcp#serverKeys()} follows the first occurrence of each key in the given collection.
     *
     * @param serverKeys the keys identifying the MCP servers, must not be null, empty, or contain blank keys.
     * @return the source.
     */
    static ToolSource mcp(Collection<String> serverKeys) {
        ensureNotEmpty(serverKeys, "serverKeys");
        Set<String> keys = new LinkedHashSet<>();
        for (String key : serverKeys) {
            keys.add(ensureNotBlank(key, "serverKey"));
        }
        return new McpToolSource(Collections.unmodifiableSet(keys));
    }

    /**
     * Creates a source for a tool registered programmatically, without an explicit source.
     *
     * @return the source.
     */
    static ToolSource programmatic() {
        return ProgrammaticToolSource.INSTANCE;
    }

    /**
     * A local {@code @Tool} method.
     *
     * @since 1.24.0
     */
    @Experimental
    sealed interface Local extends ToolSource permits LocalToolSource {

        /**
         * @return the class name as returned by {@link Class#getName()} (a binary name).
         */
        String declaringClassName();

        /**
         * @return the name of the tool method.
         */
        String methodName();

        /**
         * @return the immutable list of parameter type names as returned by {@link Class#getTypeName()}, in
         *         declaration order, which distinguishes overloads.
         */
        List<String> parameterTypeNames();
    }

    /**
     * One or more MCP servers.
     *
     * @since 1.24.0
     */
    @Experimental
    sealed interface Mcp extends ToolSource permits McpToolSource {

        /**
         * @return the immutable, non-empty set of MCP server keys involved, in insertion order.
         */
        Set<String> serverKeys();

        /**
         * @param serverKey an MCP server key.
         * @return whether the given server is involved in this tool.
         */
        boolean involves(String serverKey);
    }

    /**
     * A programmatically registered tool.
     *
     * @since 1.24.0
     */
    @Experimental
    sealed interface Programmatic extends ToolSource permits ProgrammaticToolSource {}
}

final class LocalToolSource implements ToolSource.Local {

    private final String declaringClassName;
    private final String methodName;
    private final List<String> parameterTypeNames;

    LocalToolSource(String declaringClassName, String methodName, List<String> parameterTypeNames) {
        this.declaringClassName = declaringClassName;
        this.methodName = methodName;
        this.parameterTypeNames = List.copyOf(parameterTypeNames);
    }

    @Override
    public String declaringClassName() {
        return declaringClassName;
    }

    @Override
    public String methodName() {
        return methodName;
    }

    @Override
    public List<String> parameterTypeNames() {
        return parameterTypeNames;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LocalToolSource other
                && declaringClassName.equals(other.declaringClassName)
                && methodName.equals(other.methodName)
                && parameterTypeNames.equals(other.parameterTypeNames);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(declaringClassName, methodName, parameterTypeNames);
    }

    @Override
    public String toString() {
        return "ToolSource.Local{" + declaringClassName + "#" + methodName + "(" + String.join(",", parameterTypeNames)
                + ")}";
    }
}

final class McpToolSource implements ToolSource.Mcp {

    private final Set<String> serverKeys;

    McpToolSource(Set<String> serverKeys) {
        this.serverKeys = serverKeys;
    }

    @Override
    public Set<String> serverKeys() {
        return serverKeys;
    }

    @Override
    public boolean involves(String serverKey) {
        return serverKey != null && serverKeys.contains(serverKey);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof McpToolSource other && serverKeys.equals(other.serverKeys);
    }

    @Override
    public int hashCode() {
        return serverKeys.hashCode();
    }

    @Override
    public String toString() {
        return "ToolSource.Mcp{serverKeys=" + serverKeys + "}";
    }
}

final class ProgrammaticToolSource implements ToolSource.Programmatic {

    static final ProgrammaticToolSource INSTANCE = new ProgrammaticToolSource();

    private ProgrammaticToolSource() {}

    @Override
    public String toString() {
        return "ToolSource.Programmatic";
    }
}
