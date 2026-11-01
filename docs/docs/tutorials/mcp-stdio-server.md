---
sidebar_position: 17
---

# Building a Java MCP server

LangChain4j provides an MCP **client** (`langchain4j-mcp`) for connecting to MCP servers.
If you want to build a Java-based MCP **stdio server** (a local subprocess launched by an MCP client),
use the community module: `langchain4j-community-mcp-server`.

This guide shows the minimal setup for exposing existing `@Tool`-annotated methods over MCP (JSON-RPC) via stdio.
If your server should run as a standalone service reachable over the network, see [Streamable HTTP servers](#streamable-http-servers).

## Add dependency

Add BOMs (recommended):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>${latest version here}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-community-bom</artifactId>
            <version>${latest version here}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then add the community MCP server dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-mcp-server</artifactId>
</dependency>
```

## Implement tools

Expose your functionality using `@Tool`:

```java
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

class Calculator {

    @Tool
    long add(@P("a") long a, @P("b") long b) {
        return a + b;
    }
}
```

MCP tool parameter names are taken from the Java method parameter names.
Compile your project with the [`-parameters`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html#option-parameters)
javac option, otherwise MCP clients will see generic names such as `arg0` and `arg1`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <parameters>true</parameters>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Start the stdio server

```java
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;
import java.util.List;

public class McpServerMain {

    public static void main(String[] args) throws Exception {
        McpImplementation serverInfo = new McpImplementation();
        serverInfo.setName("my-java-mcp-server");
        serverInfo.setVersion("1.0.0");

        McpServer server = new McpServer(List.of(new Calculator()), serverInfo);
        new StdioMcpServerTransport(System.in, System.out, server);

        // Keep the process alive while stdio is open
        Thread.currentThread().join();
    }
}
```

:::caution
`StdioMcpServerTransport` writes the JSON-RPC protocol to `System.out`.
Make sure your logging is configured to write to `System.err` (otherwise you will corrupt the protocol stream and the client will disconnect).
:::

## Package as a runnable JAR

MCP clients (like Claude Desktop) typically expect to start a local server process.
Packaging your server as a runnable (fat) JAR is a common approach, but any runnable process works.

## Configure an MCP client

### .mcp.json

Many MCP clients read server definitions from a `.mcp.json` file:

```json
{
  "mcpServers": {
    "my-java-tool": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/my-java-mcp-server.jar"]
    }
  }
}
```

### Claude Desktop

Add a server entry in `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "my-java-tool": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/my-java-mcp-server.jar"]
    }
  }
}
```

Use absolute paths; on Windows, escape backslashes.

## Streamable HTTP servers

The stdio transport serves a single local client per process.
If your MCP server should run as a standalone service that can be shared by several (local or remote) clients,
use the [Streamable HTTP](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#streamable-http) transport.
`langchain4j-community-mcp-server` does not provide it, but the following Java libraries do (listed alphabetically):

| Library                                                                 | Description                                                                       |
|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| [Helidon MCP](https://github.com/helidon-io/helidon-mcp)                | MCP server support for Helidon applications                                       |
| [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)        | The official Java SDK of the Model Context Protocol, independent of any framework |
| [Micronaut MCP](https://github.com/micronaut-projects/micronaut-mcp)    | MCP server support for Micronaut applications                                     |
| [Quarkus MCP Server](https://github.com/quarkiverse/quarkus-mcp-server) | MCP server support for Quarkus applications                                       |
| [Tachyon](https://github.com/tachyonmcp/tachyon)                        | MCP server library for Java and Kotlin                                            |

These are independent projects, not maintained by the LangChain4j team.
Please refer to their documentation for setup, security (authentication, TLS) and deployment.

Once your server is running, point MCP clients to its endpoint URL, for example in `.mcp.json`:

```json
{
  "mcpServers": {
    "my-java-tool": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Claude Desktop launches only local processes,
so use [`mcp-remote`](https://www.npmjs.com/package/mcp-remote) to connect it to a Streamable HTTP server:

```json
{
  "mcpServers": {
    "my-java-tool": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp"]
    }
  }
}
```
