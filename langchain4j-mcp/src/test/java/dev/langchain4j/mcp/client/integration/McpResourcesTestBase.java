package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class McpResourcesTestBase {

    static McpClient mcpClient;

    @Test
    public void resourceList() {
        List<McpResource> resourceList = mcpClient.listResources();
        assertThat(resourceList).hasSize(2);

        McpResource blob = getResourceRef("blob", resourceList);
        assertThat(blob).isNotNull();
        assertThat(blob.name()).isEqualTo("blob");
        assertThat(blob.uri()).isEqualTo("file:///blob");
        assertThat(blob.mimeType()).isEqualTo("text/blob");
        assertThat(blob.description()).isEqualTo("A nice blob");

        McpResource text = getResourceRef("text", resourceList);
        assertThat(text).isNotNull();
        assertThat(text.name()).isEqualTo("text");
        assertThat(text.uri()).isEqualTo("file:///text");
        assertThat(text.mimeType()).isEqualTo("text/plain");
        assertThat(text.description()).isEqualTo("A nice piece of text");
    }

    @Test
    public void readTextResource() {
        McpReadResourceResult response = mcpClient.readResource("file:///text");
        assertThat(response.contents()).hasSize(1);

        McpResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(McpResourceContents.Type.TEXT));
        assertThat(((McpTextResourceContents) contents).uri().toString()).isEqualTo("file:///text");
        assertThat(((McpTextResourceContents) contents).text()).isEqualTo("text");
    }

    @Test
    public void readBlobResource() {
        McpReadResourceResult response = mcpClient.readResource("file:///blob");
        assertThat(response.contents()).hasSize(1);

        McpResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(McpResourceContents.Type.BLOB));
        assertThat(((McpBlobResourceContents) contents).uri().toString()).isEqualTo("file:///blob");
        assertThat(((McpBlobResourceContents) contents).blob()).isEqualTo("blob");
    }

    @Test
    public void resourceTemplateList() {
        List<McpResourceTemplate> resourceTemplateList = mcpClient.listResourceTemplates();
        assertThat(resourceTemplateList).hasSize(2);

        McpResourceTemplate blob = getResourceTemplateRef("blob_template", resourceTemplateList);
        assertThat(blob).isNotNull();
        assertThat(blob.name()).isEqualTo("blob_template");
        assertThat(blob.uriTemplate()).isEqualTo("file:///blob-template/{message}");

        McpResourceTemplate text = getResourceTemplateRef("text_template", resourceTemplateList);
        assertThat(text).isNotNull();
        assertThat(text.name()).isEqualTo("text_template");
        assertThat(text.uriTemplate()).isEqualTo("file:///text-template/{message}");
    }

    @Test
    public void readTextResourceFromTemplate() {
        McpReadResourceResult response = mcpClient.readResource("file:///text-template/hello");
        assertThat(response.contents()).hasSize(1);

        McpResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(McpResourceContents.Type.TEXT));
        assertThat(((McpTextResourceContents) contents).uri().toString()).isEqualTo("file:///text-template/hello");
        assertThat(((McpTextResourceContents) contents).text()).isEqualTo("text hello");
    }

    @Test
    public void readBlobResourceFromTemplate() {
        McpReadResourceResult response = mcpClient.readResource("file:///blob-template/hello");
        assertThat(response.contents()).hasSize(1);

        McpResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(McpResourceContents.Type.BLOB));
        assertThat(((McpBlobResourceContents) contents).uri().toString()).isEqualTo("file:///blob-template/hello");
        assertThat(((McpBlobResourceContents) contents).blob()).isEqualTo("blob hello");
    }

    @Test
    public void readNonExistentResource() {
        Assertions.assertThatThrownBy(() -> mcpClient.readResource("file:///i-do-not-exist"))
                .isInstanceOf(McpException.class);
    }

    private McpResource getResourceRef(String resourceName, List<McpResource> resourceRefs) {
        for (McpResource ref : resourceRefs) {
            if (ref.name().equals(resourceName)) {
                return ref;
            }
        }
        return null;
    }

    private McpResourceTemplate getResourceTemplateRef(
            String resourceTemplateName, List<McpResourceTemplate> resourceTemplateRefs) {
        for (McpResourceTemplate ref : resourceTemplateRefs) {
            if (ref.name().equals(resourceTemplateName)) {
                return ref;
            }
        }
        return null;
    }
}
