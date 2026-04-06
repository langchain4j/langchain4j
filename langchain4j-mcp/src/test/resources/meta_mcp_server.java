///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS io.quarkus:quarkus-bom:${quarkus.version:3.27.0}@pom
//DEPS io.quarkiverse.mcp:quarkus-mcp-server-stdio:1.7.2

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