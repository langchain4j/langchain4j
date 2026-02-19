package dev.langchain4j.skills;

import java.util.List;

public interface Skill {

    String name();

    String description();

    String body();

    List<SkillReference> references();
}
