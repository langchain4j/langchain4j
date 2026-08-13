package dev.langchain4j.mcp.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public abstract class McpResourceSubscriptionTestBase {

    static McpClient mcpClient;
    static List<String> updatedResourceUris = new CopyOnWriteArrayList<>();

    @Test
    public void subscribeAndReceiveResourceUpdate() {
        updatedResourceUris.clear();

        long subscriptionId = mcpClient.subscribeToResources(List.of("file:///status"));

        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"updated-single\"}")
                .build());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(updatedResourceUris).contains("file:///status"));

        McpReadResourceResult result = mcpClient.readResource("file:///status");
        assertThat(((McpTextResourceContents) result.contents().get(0)).text()).isEqualTo("updated-single");

        mcpClient.unsubscribeFromResources(subscriptionId);
    }

    @Test
    public void subscribeToMultipleResourcesInOneCall() {
        updatedResourceUris.clear();

        long subscriptionId = mcpClient.subscribeToResources(List.of("file:///status", "file:///counter"));

        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"multi-status\"}")
                .build());

        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateCounter")
                .arguments("{\"newValue\": \"one\"}")
                .build());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    assertThat(updatedResourceUris).contains("file:///status");
                    assertThat(updatedResourceUris).contains("file:///counter");
                });

        McpReadResourceResult statusResult = mcpClient.readResource("file:///status");
        assertThat(((McpTextResourceContents) statusResult.contents().get(0)).text())
                .isEqualTo("multi-status");

        McpReadResourceResult counterResult = mcpClient.readResource("file:///counter");
        assertThat(((McpTextResourceContents) counterResult.contents().get(0)).text())
                .isEqualTo("one");

        mcpClient.unsubscribeFromResources(subscriptionId);
    }

    @Test
    public void unsubscribeStopsNotifications() {
        updatedResourceUris.clear();

        long subscriptionId = mcpClient.subscribeToResources(List.of("file:///status"));

        // Trigger an update and verify notification arrives
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"before-unsub\"}")
                .build());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(updatedResourceUris).contains("file:///status"));

        // Unsubscribe
        mcpClient.unsubscribeFromResources(subscriptionId);

        // Clear and trigger another update
        updatedResourceUris.clear();
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"after-unsub\"}")
                .build());

        // Verify no notification arrives after unsubscribing
        Awaitility.await()
                .during(Duration.ofSeconds(4))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    assertThat(updatedResourceUris).doesNotContain("file:///status");
                });
    }

    @Test
    public void concurrentSubscriptionsReturnDistinctIds() {
        long sub1 = mcpClient.subscribeToResources(List.of("file:///status"));
        long sub2 = mcpClient.subscribeToResources(List.of("file:///counter"));
        assertThat(sub1).isNotEqualTo(sub2);
        mcpClient.unsubscribeFromResources(sub1);
        mcpClient.unsubscribeFromResources(sub2);
    }

    @Test
    public void multipleConcurrentSubscriptions() {
        updatedResourceUris.clear();

        long statusSubscription = mcpClient.subscribeToResources(List.of("file:///status"));
        long counterSubscription = mcpClient.subscribeToResources(List.of("file:///counter"));

        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"concurrent-status\"}")
                .build());

        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateCounter")
                .arguments("{\"newValue\": \"two\"}")
                .build());

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    assertThat(updatedResourceUris).contains("file:///status");
                    assertThat(updatedResourceUris).contains("file:///counter");
                });

        McpReadResourceResult statusResult = mcpClient.readResource("file:///status");
        assertThat(((McpTextResourceContents) statusResult.contents().get(0)).text())
                .isEqualTo("concurrent-status");

        McpReadResourceResult counterResult = mcpClient.readResource("file:///counter");
        assertThat(((McpTextResourceContents) counterResult.contents().get(0)).text())
                .isEqualTo("two");

        mcpClient.unsubscribeFromResources(statusSubscription);
        mcpClient.unsubscribeFromResources(counterSubscription);
    }
}
