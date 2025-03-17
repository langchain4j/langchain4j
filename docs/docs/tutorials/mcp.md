# Model Context Protocol (MCP)

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

### MCP Transport

First, you need an instance of an MCP Transport.

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
    .logRequests(true) // if you want to see the traffic in the log
    .logResponses(true)
    .build();
```

### MCP Client

To create an MCP client from the transport:

```java
McpClient mcpClient = new DefaultMcpClient.Builder()
    .transport(transport)
    .build();
```

### MCP Tool Provider

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

More information on tool support in LangChain4j can be found [here](/tutorials/tools).

## Logging

The MCP protocol also defines a way for the server to send log messages to
the client. By default, the behavior of the client is to transform these log
messages and log them using the SLF4J logger. If you want to change this
behavior, there is an interface named
`dev.langchain4j.mcp.client.logging.McpLogMessageHandler` that serves as a
callback for received log messages. If you create your own implementation of
`McpLogMessageHandler`, pass it to the MCP client builder:

```java
McpClient mcpClient = new DefaultMcpClient.Builder()
    .transport(transport)
    .logMessageHandler(new MyLogMessageHandler())
    .build();
```

## Resources

To obtain a list of [MCP resources](https://modelcontextprotocol.io/docs/concepts/resources) 
on the server, use `client.listResources()`, or `client.listResourceTemplates()` in case of resource templates.
This will return a list of `ResourceRef` objects (or `ResourceTemplateRef` respectively). These
contain the metadata of the resource, most importantly the URI.

To obtain the actual contents of the resource, use `client.readResource(uri)`, supplying the URI of the resource.
This returns a list of `ResourceContents` objects (there may be more resource contents on a single URI, for 
example if the URI represents a directory). Each `ResourceContents` object represents either a binary blob or a
string. For a binary blob, use `resourceContents.asBlob()` to access the actual data, for text, use `resourceContents.asText()`.

## Using the GitHub MCP server through Docker

Let's now see how to use the Model Context Protocol (MCP) to bridge AI models with external tools in a standardized way.
The following example will interact with GitHub, through the LangChain4j MCP client, to fetch and summarize the latest commits from a public GitHub repository.
For that, no need to reinvent the wheel, we can use the existing [GitHub MCP server implementation](https://github.com/modelcontextprotocol/servers/tree/main/src/github) available in the [MCP GitHub repo](https://github.com/modelcontextprotocol).

The idea is to build a Java application that connects to a GitHub MCP server running locally in Docker, to fetch and summarize the latest commits.
The example uses the stdio transport mechanism of MCP to communicate between our Java application and the GitHub MCP server.

## Packaging and Executing the GitHub MCP Server in Docker

To interact with GitHub, we first need to set up the GitHub MCP server in Docker.
The GitHub MCP server provides a standardized interface to interact with GitHub through the Model Context Protocol.
It enables file operations, repository management, and search functionality. 

To build the Docker image for our GitHub MCP server, you need to get the code from the [MCP servers GitHub repo](https://github.com/modelcontextprotocol/servers/tree/main/src/github) either by cloning the repo or downloading the code.
Then, navigate to the root directory and execute the following Docker command:

```bash
docker build -t mcp/github -f src/github/Dockerfile .
```
The `Dockerfile` sets up the necessary environment and installs the GitHub MCP server implementation. 
Once built, the image will be available locally as `mcp/github`.

```bash
docker image ls

REPOSITORY   TAG         IMAGE ID        SIZE
mcp/github   latest      b141704170b1    173MB
```

## Developing the Tool Provider

Let's create a Java class called `McpGithubToolsExample` that uses LangChain4j to connect to our GitHub MCP server. This class will:

* Start the GitHub MCP server in a Docker container (the `docker` command is available in `/usr/local/bin/docker`)
* Establish a connection using the stdio transport
* Use an LLM to summarize the last 3 commits of the LangChain4j GitHub repository

> **Note**: In the code below we pass the GitHub token in the environment variable `GITHUB_PERSONAL_ACCESS_TOKEN`. But this is optional for some actions on public repositories that don't need authentication.

Here's the implementation:

```java
public static void main(String[] args) throws Exception {

    ChatLanguageModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .logRequests(true)
        .logResponses(true)
        .build();

    McpTransport transport = new StdioMcpTransport.Builder()
        .command(List.of("/usr/local/bin/docker", "run", "-e", "GITHUB_PERSONAL_ACCESS_TOKEN", "-i", "mcp/github"))
        .logEvents(true)
        .build();

    McpClient mcpClient = new DefaultMcpClient.Builder()
        .transport(transport)
        .build();

    ToolProvider toolProvider = McpToolProvider.builder()
        .mcpClients(List.of(mcpClient))
        .build();

    Bot bot = AiServices.builder(Bot.class)
        .chatLanguageModel(model)
        .toolProvider(toolProvider)
        .build();

    try {
        String response = bot.chat("Summarize the last 3 commits of the LangChain4j GitHub repository");
        System.out.println("RESPONSE: " + response);
    } finally {
        mcpClient.close();
    }
}
```

> **Note**: This example uses Docker and therefore executes a Docker command available in `/usr/local/bin/docker` (change the path according to your operating system). If you want to use Podman instead of Docker, change the command accordingly.

## Executing the Code

To run the example, make sure that Docker is up and running on your system.
Also, set your OpenAI API key in the environment variable `OPENAI_API_KEY`.

Then run the Java application. You should get a response summarizing the last 3 commits of the LangChain4j GitHub repository, such as:

```
Here are the summaries of the last three commits in the LangChain4j GitHub repository:

1. **Commit [36951f9](https://github.com/langchain4j/langchain4j/commit/36951f9649c1beacd8b9fc2d910a2e23223e0d93)** (Date: 2025-02-05)
   - **Author:** Dmytro Liubarskyi
   - **Message:** Updated to `upload-pages-artifact@v3`.
   - **Details:** This commit updates the GitHub Action used for uploading pages artifacts to version 3.

2. **Commit [6fcd19f](https://github.com/langchain4j/langchain4j/commit/6fcd19f50c8393729a0878d6125b0bb1967ac055)** (Date: 2025-02-05)
   - **Author:** Dmytro Liubarskyi
   - **Message:** Updated to `checkout@v4`, `deploy-pages@v4`, and `upload-pages-artifact@v4`.
   - **Details:** This commit updates multiple GitHub Actions to their version 4.

3. **Commit [2e74049](https://github.com/langchain4j/langchain4j/commit/2e740495d2aa0f16ef1c05cfcc76f91aef6f6599)** (Date: 2025-02-05)
   - **Author:** Dmytro Liubarskyi
   - **Message:** Updated to `setup-node@v4` and `configure-pages@v4`.
   - **Details:** This commit updates the `setup-node` and `configure-pages` GitHub Actions to version 4.

All commits were made by the same author, Dmytro Liubarskyi, on the same day, focusing on updating various GitHub Actions to newer versions.
```
