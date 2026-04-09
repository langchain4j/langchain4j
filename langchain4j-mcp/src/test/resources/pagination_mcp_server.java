///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.7.2

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkiverse.mcp.server.Tool;

public class pagination_mcp_server {

    // 5 tools (with page size 2, that's 3 pages)

    @Tool(description = "Tool number one")
    public String tool1() {
        return "result1";
    }

    @Tool(description = "Tool number two")
    public String tool2() {
        return "result2";
    }

    @Tool(description = "Tool number three")
    public String tool3() {
        return "result3";
    }

    @Tool(description = "Tool number four")
    public String tool4() {
        return "result4";
    }

    @Tool(description = "Tool number five")
    public String tool5() {
        return "result5";
    }

    // 5 resources (with page size 2, that's 3 pages)

    @Resource(uri = "file:///resource1", description = "Resource one")
    TextResourceContents resource1() {
        return TextResourceContents.create("file:///resource1", "content1");
    }

    @Resource(uri = "file:///resource2", description = "Resource two")
    TextResourceContents resource2() {
        return TextResourceContents.create("file:///resource2", "content2");
    }

    @Resource(uri = "file:///resource3", description = "Resource three")
    TextResourceContents resource3() {
        return TextResourceContents.create("file:///resource3", "content3");
    }

    @Resource(uri = "file:///resource4", description = "Resource four")
    TextResourceContents resource4() {
        return TextResourceContents.create("file:///resource4", "content4");
    }

    @Resource(uri = "file:///resource5", description = "Resource five")
    TextResourceContents resource5() {
        return TextResourceContents.create("file:///resource5", "content5");
    }

    // 5 prompts (with page size 2, that's 3 pages)

    @Prompt(description = "Prompt number one")
    PromptMessage prompt1() {
        return PromptMessage.withUserRole(new TextContent("Hello from prompt1"));
    }

    @Prompt(description = "Prompt number two")
    PromptMessage prompt2() {
        return PromptMessage.withUserRole(new TextContent("Hello from prompt2"));
    }

    @Prompt(description = "Prompt number three")
    PromptMessage prompt3() {
        return PromptMessage.withUserRole(new TextContent("Hello from prompt3"));
    }

    @Prompt(description = "Prompt number four")
    PromptMessage prompt4() {
        return PromptMessage.withUserRole(new TextContent("Hello from prompt4"));
    }

    @Prompt(description = "Prompt number five")
    PromptMessage prompt5() {
        return PromptMessage.withUserRole(new TextContent("Hello from prompt5"));
    }
}