---
sidebar_position: 17
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Building a Java MCP server

LangChain4j provides an MCP **client** (`langchain4j-mcp`) for connecting to MCP servers.
This guide shows how to build a Java-based MCP **server** that exposes your existing `@Tool`-annotated methods
over MCP (JSON-RPC), using either the **stdio** or the **Streamable HTTP** transport.

## Choose a transport

|             | stdio                                          | Streamable HTTP                                 |
|-------------|------------------------------------------------|-------------------------------------------------|
| Runs as     | Local subprocess launched by the MCP client    | Standalone HTTP service                         |
| Clients     | Single local client per process                | Local or remote, multiple concurrent clients    |
| Library     | `langchain4j-community-mcp-server`             | [Tachyon](https://tachyonmcp.dev)               |
| Best for    | Desktop tools, CLI integrations                | Shared or deployed services                     |

Select a transport below. Switching tabs updates all transport-specific examples on this page.

## Add dependencies

It's recommended to use BOMs along with server dependency:

<Tabs groupId="dependencies" queryString>
  <TabItem value="stdio" label="stdio" default>

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

<dependencies>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-mcp-server</artifactId>
    </dependency>
</dependencies>
```

  </TabItem>
  <TabItem value="http" label="Streamable HTTP">

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
            <!-- check latest version on https://mvnrepository.com/artifact/dev.tachyonmcp/tachyon-bom -->
            <groupId>dev.tachyonmcp</groupId>
            <artifactId>tachyon-bom</artifactId>
            <version>${tachyon.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-core</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.tachyonmcp</groupId>
        <artifactId>tachyon-core</artifactId>
    </dependency>
    <dependency>
        <groupId>dev.tachyonmcp</groupId>
        <artifactId>tachyon-annotations-langchain4j</artifactId>
    </dependency>
</dependencies>
```

  </TabItem>
</Tabs>

## Implement tools

Expose your functionality using `@Tool`. The same class works with both transports:

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

To derive MCP tool parameter names from Java method parameter names using reflection, compile the project
with the [`javac parameters`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html#option-parameters) flag.
Add the following to `pom.xml`:

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

## Start the server

<Tabs groupId="create-server" queryString>
  <TabItem value="stdio" label="stdio" default>

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

  </TabItem>
  <TabItem value="http" label="Streamable HTTP">

```java
import dev.tachyonmcp.annotations.langchain4j.LangChain4jAnnotationProvider;
import dev.tachyonmcp.core.server.TachyonServer;

public class McpServerMain {

    public static void main(String[] args) throws Exception {
        final var server = TachyonServer.builder()
                .host("127.0.0.1")
                .port(8080)
                .info(it -> it.name("my-java-mcp-server")
                        .description("MCP server scanning a LangChain4j @Tool method via annotations")
                        .version("1.0.0"))
                .annotations(a -> a
                        .withProvider(LangChain4jAnnotationProvider.instance())
                        .register(new Calculator()))
                .build();

        server.start();
    }
}
```

You may find a Tachyon + LangChain4j example at [here](https://github.com/langchain4j/langchain4j-examples/tree/main/tachyon-streamable-http-example). 

:::note
Binding to `127.0.0.1` makes the server reachable only from the local machine.
To accept remote clients, bind to `0.0.0.0` (or a specific interface) and put authentication/TLS in front of it.

Please refer to the [Tachyon documentation](https://tachyonmcp.dev/docs/) for details.
:::

  </TabItem>
</Tabs>

## Run the server

<Tabs groupId="run-server" queryString>
  <TabItem value="stdio" label="stdio" default>

MCP clients (like Claude Desktop) start the server process themselves.
Packaging your server as a runnable (fat) JAR is a common approach, but any runnable process works.

  </TabItem>
  <TabItem value="http" label="Streamable HTTP">

Start the application yourself (for example, package it and run `java -jar my-java-mcp-server.jar`).
The MCP endpoint is then available at `http://127.0.0.1:8080/mcp`.

  </TabItem>
</Tabs>

## Configure an MCP client

### .mcp.json

<Tabs groupId="config-mcp-json" queryString>
  <TabItem value="stdio" label="stdio" default>

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
  </TabItem>
  <TabItem value="http" label="Streamable HTTP">

```json
{
  "mcpServers": {
    "my-java-tool": {
      "type": "http",
      "url": "http://127.0.0.1:8080/mcp"
    }
  }
}
```

  </TabItem>
</Tabs>

### Claude Desktop

Add a server entry in `claude_desktop_config.json`:

<Tabs groupId="config-claude-desktop" queryString>
  <TabItem value="stdio" label="stdio" default>

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

  </TabItem>
  <TabItem value="http" label="Streamable HTTP">

Since Claude Desktop launches only local processes, use `mcp-remote` to connect it to Streamable HTTP MCP server:

```json
{
  "mcpServers": {
    "my-java-tool": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://127.0.0.1:8080/mcp"]
    }
  }
}
```

  </TabItem>
</Tabs>
