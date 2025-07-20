package dev.langchain4j.rag.content.injector;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultContentInjectorTest {

    @Test
    void should_not_inject_when_no_content() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        List<Content> contents = emptyList();

        ContentInjector injector = new DefaultContentInjector();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected).isEqualTo(userMessage);
    }

    @Test
    void should_inject_single_content() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        List<Content> contents = singletonList(Content.from("Bananas are awesome!"));

        ContentInjector injector = new DefaultContentInjector();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo("""
                Tell me about bananas.
                
                Answer using the following information:
                Bananas are awesome!""".stripIndent()
        );
    }

    @Test
    void should_inject_single_content_with_userName() {
        // given
        UserMessage userMessage = UserMessage.from("ape", "Tell me about bananas.");

        List<Content> contents = singletonList(Content.from("Bananas are awesome!"));

        ContentInjector injector = new DefaultContentInjector();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo(
                """
                        Tell me about bananas.
                        
                        Answer using the following information:
                        Bananas are awesome!"""
        );
        assertThat(injected.name()).isEqualTo("ape");
    }

    @Test
    void should_inject_single_content_with_metadata() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        TextSegment segment = TextSegment.from(
                "Bananas are awesome!",
                Metadata.from("source", "trust me bro")
        );
        List<Content> contents = singletonList(Content.from(segment));

        List<String> metadataKeysToInclude = singletonList("source");

        ContentInjector injector = new DefaultContentInjector(metadataKeysToInclude);

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo(
                """
                        Tell me about bananas.
                        
                        Answer using the following information:
                        content: Bananas are awesome!
                        source: trust me bro"""
        );
    }

    @Test
    void should_inject_multiple_contents() {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        List<Content> contents = asList(
                Content.from("Bananas are awesome!"),
                Content.from("Bananas are healthy!")
        );

        ContentInjector injector = new DefaultContentInjector();

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo(
                """
                        Tell me about bananas.
                        
                        Answer using the following information:
                        Bananas are awesome!
                        
                        Bananas are healthy!"""
        );
    }

    @ParameterizedTest
    @MethodSource
    void should_inject_multiple_contents_with_multiple_metadata_entries(
            Function<List<String>, ContentInjector> contentInjectorProvider
    ) {

        // given
        UserMessage userMessage = UserMessage.from("Tell me about bananas.");

        TextSegment segment1 = TextSegment.from(
                "Bananas are awesome!",
                Metadata.from("source", "trust me bro")
                        .put("date", "today")
        );
        TextSegment segment2 = TextSegment.from(
                "Bananas are healthy!",
                Metadata.from("source", "my doctor")
                        .put("reliability", "100%")
        );
        List<Content> contents = asList(Content.from(segment1), Content.from(segment2));

        List<String> metadataKeysToInclude = asList("source", "reliability", "date");

        ContentInjector injector = contentInjectorProvider.apply(metadataKeysToInclude);

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo(
                """
                        Tell me about bananas.
                        
                        Answer using the following information:
                        content: Bananas are awesome!
                        source: trust me bro
                        date: today
                        
                        content: Bananas are healthy!
                        source: my doctor
                        reliability: 100%"""
        );
    }

    static Stream<Arguments> should_inject_multiple_contents_with_multiple_metadata_entries() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        (Function<List<String>, ContentInjector>) DefaultContentInjector::new
                ))
                .add(Arguments.of(
                        (Function<List<String>, ContentInjector>)
                                (metadataKeysToInclude) -> DefaultContentInjector.builder()
                                        .metadataKeysToInclude(metadataKeysToInclude)
                                        .build()
                ))
                .build();
    }

    @ParameterizedTest
    @MethodSource
    void should_inject_multiple_contents_with_custom_prompt_template(
            Function<PromptTemplate, ContentInjector> contentInjectorProvider) {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from("{{userMessage}}\n{{contents}}");

        UserMessage userMessage = UserMessage.from("Tell me about bananas.");
        List<Content> contents = asList(
                Content.from("Bananas are awesome!"),
                Content.from("Bananas are healthy!")
        );
        ContentInjector injector = contentInjectorProvider.apply(promptTemplate);

        // when
        UserMessage injected = (UserMessage) injector.inject(contents, userMessage);

        // then
        assertThat(injected.singleText()).isEqualTo(
                """
                        Tell me about bananas.
                        Bananas are awesome!
                        
                        Bananas are healthy!"""
        );
    }

    static Stream<Arguments> should_inject_multiple_contents_with_custom_prompt_template() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(
                        (Function<PromptTemplate, ContentInjector>) DefaultContentInjector::new
                ))
                .add(Arguments.of(
                        (Function<PromptTemplate, ContentInjector>)
                                (promptTemplate) -> DefaultContentInjector.builder()
                                        .promptTemplate(promptTemplate)
                                        .build()
                ))
                .build();
    }
}
