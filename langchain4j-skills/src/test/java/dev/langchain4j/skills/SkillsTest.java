package dev.langchain4j.skills;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsTest {

    @Test
    void should_generate_description_for_single_skill() {
        Skills skills = Skills.from(
                Skill.builder()
                        .name("docx")
                        .description("Edit Word documents")
                        .content("skill content")
                        .build()
        );

        assertThat(skills.formatNamesAndDescriptions()).isEqualTo(
                """
                <available_skills>
                <skill>
                <name>docx</name>
                <description>Edit Word documents</description>
                </skill>
                </available_skills>"""
        );
    }

    @Test
    void should_generate_description_for_multiple_skills() {
        Skills skills = Skills.from(
                Skill.builder()
                        .name("docx")
                        .description("Edit Word documents")
                        .content("docx content")
                        .build(),
                Skill.builder()
                        .name("mcp-builder")
                        .description("Build MCP servers")
                        .content("mcp content")
                        .build()
        );

        assertThat(skills.formatNamesAndDescriptions()).isEqualTo(
                """
                <available_skills>
                <skill>
                <name>docx</name>
                <description>Edit Word documents</description>
                </skill>
                <skill>
                <name>mcp-builder</name>
                <description>Build MCP servers</description>
                </skill>
                </available_skills>"""
        );
    }

    @Test
    void should_escape_xml_special_characters_in_name_and_description() {
        Skills skills = Skills.from(
                Skill.builder()
                        .name("skill<>&\"'")
                        .description("desc<>&\"'")
                        .content("content")
                        .build()
        );

        assertThat(skills.formatNamesAndDescriptions()).isEqualTo(
                """
                <available_skills>
                <skill>
                <name>skill&lt;&gt;&amp;&quot;&apos;</name>
                <description>desc&lt;&gt;&amp;&quot;&apos;</description>
                </skill>
                </available_skills>"""
        );
    }
}
