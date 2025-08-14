///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.25.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.4.0
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-sse:1.4.0

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;

public class resources_alice_mcp_server {

    @Resource(uri = "file:///info", description = "Basic information about Alice", mimeType = "text/plain")
    TextResourceContents basicInfo() {
        return TextResourceContents.create("file:///info", "Alice was born in 1962 and lives in Manchester.");
    }

}
