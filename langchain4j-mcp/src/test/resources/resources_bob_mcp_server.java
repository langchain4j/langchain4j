///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-http:2.0.0.Beta3
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-websocket:2.0.0.Beta3

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;

public class resources_bob_mcp_server {

    @Resource(uri = "file:///info", description = "Basic information about Bob", mimeType = "text/plain")
    TextResourceContents basicInfo() {
        return TextResourceContents.create("file:///info", "Bob was born in 1956 and lives in London.");
    }

}
