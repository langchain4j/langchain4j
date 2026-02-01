---
sidebar_position: 17
---

# Building a Java MCP stdio server

LangChain4j provides an MCP **client** (`langchain4j-mcp`) for connecting to MCP servers.
If you want to build a Java-based MCP **stdio server** (a local subprocess launched by an MCP client),
use the community module: `langchain4j-community-mcp-server`.

This guide shows the minimal setup for exposing existing `@Tool`-annotated methods over MCP (JSON-RPC) via stdio.

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

## Complete runnable example

See the `mcp-stdio-server-example` in the examples repository for an end-to-end runnable project (including packaging and client config):

- https://github.com/langchain4j/langchain4j-examples (directory: `mcp-stdio-server-example`)
