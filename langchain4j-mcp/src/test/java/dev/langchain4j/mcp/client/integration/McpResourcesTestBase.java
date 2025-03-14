package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.ResourceContents;
import dev.langchain4j.mcp.client.ResourceRef;
import dev.langchain4j.mcp.client.ResourceResponse;
import dev.langchain4j.mcp.client.ResourceTemplateRef;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class McpResourcesTestBase {

    static McpClient mcpClient;

    @Test
    public void resourceList() {
        List<ResourceRef> resourceList = mcpClient.listResources();
        assertThat(resourceList).hasSize(2);

        ResourceRef blob = getResourceRef("blob", resourceList);
        assertThat(blob).isNotNull();
        assertThat(blob.name()).isEqualTo("blob");
        assertThat(blob.uri()).isEqualTo("file:///blob");
        assertThat(blob.mimeType()).isEqualTo("text/blob");
        assertThat(blob.description()).isEqualTo("A nice blob");

        ResourceRef text = getResourceRef("text", resourceList);
        assertThat(text).isNotNull();
        assertThat(text.name()).isEqualTo("text");
        assertThat(text.uri()).isEqualTo("file:///text");
        assertThat(text.mimeType()).isEqualTo("text/plain");
        assertThat(text.description()).isEqualTo("A nice piece of text");
    }

    @Test
    public void readTextResource() {
        ResourceResponse response = mcpClient.readResource("file:///text");
        assertThat(response.contents()).hasSize(1);

        ResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(ResourceContents.Type.TEXT));
        assertThat(contents.asText().uri()).hasToString("file:///text");
        assertThat(contents.asText().text()).isEqualTo("text");
    }

    @Test
    public void readBlobResource() {
        ResourceResponse response = mcpClient.readResource("file:///blob");
        assertThat(response.contents()).hasSize(1);

        ResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(ResourceContents.Type.BLOB));
        assertThat(contents.asBlob().uri()).hasToString("file:///blob");
        assertThat(contents.asBlob().blob()).isEqualTo("blob");
    }

    @Test
    public void resourceTemplateList() {
        List<ResourceTemplateRef> resourceTemplateList = mcpClient.listResourceTemplates();
        assertThat(resourceTemplateList).hasSize(2);

        ResourceTemplateRef blob = getResourceTemplateRef("blob_template", resourceTemplateList);
        assertThat(blob).isNotNull();
        assertThat(blob.name()).isEqualTo("blob_template");
        assertThat(blob.uriTemplate()).isEqualTo("file:///blob-template/{message}");

        ResourceTemplateRef text = getResourceTemplateRef("text_template", resourceTemplateList);
        assertThat(text).isNotNull();
        assertThat(text.name()).isEqualTo("text_template");
        assertThat(text.uriTemplate()).isEqualTo("file:///text-template/{message}");
    }

    @Test
    public void readTextResourceFromTemplate() {
        ResourceResponse response = mcpClient.readResource("file:///text-template/hello");
        assertThat(response.contents()).hasSize(1);

        ResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(ResourceContents.Type.TEXT));
        assertThat(contents.asText().uri()).hasToString("file:///text-template/hello");
        assertThat(contents.asText().text()).isEqualTo("text hello");
    }

    @Test
    public void readBlobResourceFromTemplate() {
        ResourceResponse response = mcpClient.readResource("file:///blob-template/hello");
        assertThat(response.contents()).hasSize(1);

        ResourceContents contents = response.contents().get(0);
        assertThat(contents.type().equals(ResourceContents.Type.BLOB));
        assertThat(contents.asBlob().uri()).hasToString("file:///blob-template/hello");
        assertThat(contents.asBlob().blob()).isEqualTo("blob hello");
    }

    private ResourceRef getResourceRef(String resourceName, List<ResourceRef> resourceRefs) {
        for (ResourceRef ref : resourceRefs) {
            if (ref.name().equals(resourceName)) {
                return ref;
            }
        }
        return null;
    }

    private ResourceTemplateRef getResourceTemplateRef(
            String resourceTemplateName, List<ResourceTemplateRef> resourceTemplateRefs) {
        for (ResourceTemplateRef ref : resourceTemplateRefs) {
            if (ref.name().equals(resourceTemplateName)) {
                return ref;
            }
        }
        return null;
    }
}
