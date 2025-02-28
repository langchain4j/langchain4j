package dev.langchain4j.store.embedding.coherence;

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
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CoherenceEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    @RegisterExtension
    static TestLogsExtension testLogs = new TestLogsExtension();

    @RegisterExtension
    static CoherenceClusterExtension cluster = new CoherenceClusterExtension()
            .with(
                    ClusterName.of("CoherenceEmbeddingStoreRemovalIT"),
                    WellKnownAddress.loopback(),
                    LocalHost.only(),
                    IPv4Preferred.autoDetect(),
                    SystemProperty.of("coherence.serializer", "pof"))
            .include(3, CoherenceClusterMember.class, DisplayName.of("storage"), RoleName.of("storage"), testLogs);

    static Session session;

    static EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static CoherenceEmbeddingStore embeddingStore;

    @BeforeAll
    static void beforeAll() {
        session = cluster.buildSession(SessionBuilders.storageDisabledMember(RoleName.of("test")));
        embeddingStore = CoherenceEmbeddingStore.builder().session(session).build();
    }

    @BeforeEach
    void clearEmbeddings() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return model;
    }
}
