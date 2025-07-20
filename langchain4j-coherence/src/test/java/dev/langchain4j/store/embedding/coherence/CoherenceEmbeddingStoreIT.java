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
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * An extension of the LangChain4j {@link EmbeddingStoreIT} tests
 * that use Coherence as an {@link EmbeddingStore}.
 */
class CoherenceEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @RegisterExtension
    static TestLogsExtension testLogs = new TestLogsExtension();

    @RegisterExtension
    static CoherenceClusterExtension cluster = new CoherenceClusterExtension()
            .with(
                    ClusterName.of("CoherenceEmbeddingStoreIT"),
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

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return model;
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }
}
