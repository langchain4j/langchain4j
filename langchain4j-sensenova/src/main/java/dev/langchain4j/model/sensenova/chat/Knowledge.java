package dev.langchain4j.model.sensenova.chat;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Knowledge {

	private String content;

	private KnowledgeInjectionMode mode;
}
