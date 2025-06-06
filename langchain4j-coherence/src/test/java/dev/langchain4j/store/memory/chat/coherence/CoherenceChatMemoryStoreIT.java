package dev.langchain4j.store.memory.chat.coherence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracle.bedrock.junit.CoherenceClusterExtension;
import com.oracle.bedrock.junit.SessionBuilders;
import com.oracle.bedrock.runtime.coherence.CoherenceClusterMember;
import com.oracle.bedrock.runtime.coherence.options.ClusterName;
import com.oracle.bedrock.runtime.coherence.options.LocalHost;
import com.oracle.bedrock.runtime.coherence.options.RoleName;
import com.oracle.bedrock.runtime.coherence.options.WellKnownAddress;
import com.oracle.bedrock.runtime.java.options.IPv4Preferred;
import com.oracle.bedrock.runtime.java.options.SystemProperty;
import com.oracle.bedrock.runtime.options.DisplayName;
import com.oracle.bedrock.testsupport.junit.TestLogsExtension;
import com.tangosol.net.Session;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

class CoherenceChatMemoryStoreIT {

    @RegisterExtension
    static TestLogsExtension testLogs = new TestLogsExtension();

    @RegisterExtension
    static CoherenceClusterExtension cluster = new CoherenceClusterExtension()
            .with(
                    ClusterName.of("NamedMapEmbeddingRepositoryIT"),
                    WellKnownAddress.loopback(),
                    LocalHost.only(),
                    IPv4Preferred.autoDetect(),
                    SystemProperty.of("coherence.serializer", "pof"))
            .include(3, CoherenceClusterMember.class, DisplayName.of("storage"), RoleName.of("storage"), testLogs);

    static Session session;

    private final String userId = "someUserId";

    private CoherenceChatMemoryStore memoryStore;

    @BeforeAll
    static void beforeAll() {
        session = cluster.buildSession(SessionBuilders.storageDisabledMember(RoleName.of("test")));
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        this.memoryStore = CoherenceChatMemoryStore.builder()
                .session(session)
                .name(testInfo.getDisplayName())
                .build();
        memoryStore.deleteMessages(userId);
        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).isEmpty();
    }

    @Test
    void should_set_messages_into_coherence() {
        // given
        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).isEmpty();

        // when
        List<ChatMessage> chatMessages = new ArrayList<>();
        String sysMessage = "You are a large language model working with LangChain4j";
        chatMessages.add(new SystemMessage(sysMessage));
        List<Content> userMsgContents = new ArrayList<>();
        userMsgContents.add(new ImageContent("someCatImageUrl"));
        chatMessages.add(new UserMessage("user1", userMsgContents));
        memoryStore.updateMessages(userId, chatMessages);

        // then
        messages = memoryStore.getMessages(userId);
        assertThat(messages).hasSize(2);

        assertThat(messages.get(0).type()).isEqualTo(ChatMessageType.SYSTEM);
        assertThat(messages.get(1).type()).isEqualTo(ChatMessageType.USER);

        SystemMessage sys = (SystemMessage) messages.get(0);
        assertThat(sys.text()).isEqualTo(sysMessage);

        UserMessage usr = (UserMessage) messages.get(1);
        assertThat(usr.contents()).isEqualTo(userMsgContents);
    }

    @Test
    void should_delete_messages_from_coherence() {
        // given
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with LangChain4j"));
        memoryStore.updateMessages(userId, chatMessages);
        List<ChatMessage> messages = memoryStore.getMessages(userId);
        assertThat(messages).hasSize(1);

        // when
        memoryStore.deleteMessages(userId);

        // then
        messages = memoryStore.getMessages(userId);
        assertThat(messages).isEmpty();
    }

    @Test
    void getMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.getMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null");
    }

    @Test
    void updateMessages_messages_null() {
        assertThatThrownBy(() -> memoryStore.updateMessages(userId, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> memoryStore.updateMessages(userId, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_null() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with LangChain4j"));
        assertThatThrownBy(() -> memoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null");
    }

    @Test
    void deleteMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null");
    }
}
