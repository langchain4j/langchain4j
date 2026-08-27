///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.33.2.1}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:2.0.0.Beta3

import io.quarkiverse.mcp.server.Meta;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class meta_mcp_server {

    @Tool(description = "Echoes the value of a _meta field")
    public String echoMeta(Meta meta, @ToolArg(description = "The key to look up in _meta") String key) {
        Object value = meta.asJsonObject().getValue(key);
        return value != null ? value.toString() : "null";
    }
}