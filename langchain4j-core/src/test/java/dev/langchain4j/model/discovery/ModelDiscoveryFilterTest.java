package dev.langchain4j.model.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.Capability;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelDiscoveryFilterTest {

    @Test
    void should_build_empty_filter() {
        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder().build();

        assertThat(filter.getTypes()).isNull();
        assertThat(filter.getRequiredCapabilities()).isNull();
        assertThat(filter.getMinContextWindow()).isNull();
        assertThat(filter.getMaxContextWindow()).isNull();
        assertThat(filter.getNamePattern()).isNull();
        assertThat(filter.getIncludeDeprecated()).isNull();
        assertThat(filter.matchesAll()).isTrue();
    }

    @Test
    void should_build_with_all_fields() {
        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
                .types(Set.of(ModelType.CHAT, ModelType.EMBEDDING))
                .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                .minContextWindow(100000)
                .maxContextWindow(200000)
                .namePattern("gpt.*")
                .includeDeprecated(false)
                .build();

        assertThat(filter.getTypes()).containsExactlyInAnyOrder(ModelType.CHAT, ModelType.EMBEDDING);
        assertThat(filter.getRequiredCapabilities()).containsExactly(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        assertThat(filter.getMinContextWindow()).isEqualTo(100000);
        assertThat(filter.getMaxContextWindow()).isEqualTo(200000);
        assertThat(filter.getNamePattern()).isEqualTo("gpt.*");
        assertThat(filter.getIncludeDeprecated()).isFalse();
        assertThat(filter.matchesAll()).isFalse();
    }

    @Test
    void should_have_static_ALL_filter() {
        assertThat(ModelDiscoveryFilter.ALL).isNotNull();
        assertThat(ModelDiscoveryFilter.ALL.matchesAll()).isTrue();
    }

    @Test
    void should_detect_matchesAll_correctly() {
        ModelDiscoveryFilter emptyFilter = ModelDiscoveryFilter.builder().build();
        assertThat(emptyFilter.matchesAll()).isTrue();

        ModelDiscoveryFilter filterWithTypes =
                ModelDiscoveryFilter.builder().types(Set.of(ModelType.CHAT)).build();
        assertThat(filterWithTypes.matchesAll()).isFalse();

        ModelDiscoveryFilter filterWithCapabilities = ModelDiscoveryFilter.builder()
                .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                .build();
        assertThat(filterWithCapabilities.matchesAll()).isFalse();

        ModelDiscoveryFilter filterWithMinContext =
                ModelDiscoveryFilter.builder().minContextWindow(100000).build();
        assertThat(filterWithMinContext.matchesAll()).isFalse();

        ModelDiscoveryFilter filterWithMaxContext =
                ModelDiscoveryFilter.builder().maxContextWindow(200000).build();
        assertThat(filterWithMaxContext.matchesAll()).isFalse();

        ModelDiscoveryFilter filterWithPattern =
                ModelDiscoveryFilter.builder().namePattern("test").build();
        assertThat(filterWithPattern.matchesAll()).isFalse();

        ModelDiscoveryFilter filterWithDeprecated =
                ModelDiscoveryFilter.builder().includeDeprecated(false).build();
        assertThat(filterWithDeprecated.matchesAll()).isFalse();
    }

    @Test
    void should_implement_equals_and_hashCode() {
        ModelDiscoveryFilter filter1 = ModelDiscoveryFilter.builder()
                .types(Set.of(ModelType.CHAT))
                .minContextWindow(100000)
                .build();

        ModelDiscoveryFilter filter2 = ModelDiscoveryFilter.builder()
                .types(Set.of(ModelType.CHAT))
                .minContextWindow(100000)
                .build();

        ModelDiscoveryFilter filter3 = ModelDiscoveryFilter.builder()
                .types(Set.of(ModelType.EMBEDDING))
                .minContextWindow(100000)
                .build();

        assertThat(filter1).isEqualTo(filter2);
        assertThat(filter1).hasSameHashCodeAs(filter2);
        assertThat(filter1).isNotEqualTo(filter3);
    }

    @Test
    void should_implement_toString() {
        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
                .types(Set.of(ModelType.CHAT))
                .minContextWindow(100000)
                .includeDeprecated(false)
                .build();

        String str = filter.toString();

        assertThat(str).contains("CHAT");
        assertThat(str).contains("100000");
        assertThat(str).contains("false");
    }

    @Test
    void should_make_immutable_copies_of_collections() {
        Set<ModelType> types = Set.of(ModelType.CHAT);
        Set<Capability> capabilities = Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);

        ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
                .types(types)
                .requiredCapabilities(capabilities)
                .build();

        assertThat(filter.getTypes()).isNotSameAs(types);
        assertThat(filter.getRequiredCapabilities()).isNotSameAs(capabilities);
    }
}
