# Model Context Protocol

LangChain4j supports the Model Context Protocol (MCP) to communicate with
MCP compliant servers that can provide and execute tools. General
information about the protocol can be found at the [MCP
website](https://modelcontextprotocol.io/).

The protocol specifies two types of transport, both of these are supported:

- `HTTP`: The client requests an SSE channel to receive events from the
  server and then sends commands via HTTP POST requests.
- `stdio`: The client can run an MCP server as a local subprocess and
  communicate with it directly via standard input/output.

To let your chat model or AI service run tools provided by an MCP server,
you need to create an instance of an MCP tool provider.

## Creating an MCP tool provider

### Transport

First, you need an instance of a Transport.

For stdio - this example shows how to start a server from a NPM package as a subprocess:

```java
McpTransport transport = new StdioMcpTransport.Builder()
    .command(List.of("/usr/bin/npm", "exec", "@modelcontextprotocol/server-everything@0.6.2"))
    .logEvents(true) // only if you want to see the traffic in the log
    .build();
```

For HTTP, you need two URLs, one for starting the SSE channel and one for submitting commands via `POST`:

```java
McpTransport transport = new HttpMcpTransport.Builder()
    .sseUrl("http://localhost:3001/sse")
    .postUrl("http://localhost:3001/message")
    .logRequests(true) // if you want to see the traffic in the log
    .logResponses(true)
    .build();
```

### Client

To create an MCP client from the transport:

```java
McpClient mcpClient = new DefaultMcpClient.Builder()
    .transport(transport)
    .build();
```

### Tool provider

Finally, you create an MCP tool provider from the client:

```java
ToolProvider toolProvider = McpToolProvider.builder()
    .mcpClients(List.of(mcpClient))
    .build();
```

Note that one MCP tool provider can use multiple clients at the same time.
If you make use of this, you can also specify the behavior of the tool provider
in cases when retrieving tools from a particular server fails - this is done
by the `builder.failIfOneServerFails(boolean)` method. The default is `false`, 
which means that the tool provider will ignore the error from one server and 
continue with the other servers. If you set it to `true`, a failure from any 
server will cause the tool provider to throw an exception.

To bind a tool provider to an AI service, simply use the `toolProvider` method
of an AI service builder:

```java
Bot bot = AiServices.builder(Bot.class)
    .chatLanguageModel(model)
    .toolProvider(toolProvider)
    .build();
```
