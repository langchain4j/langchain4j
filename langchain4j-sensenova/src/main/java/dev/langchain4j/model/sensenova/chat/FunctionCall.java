package dev.langchain4j.model.sensenova.chat;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class FunctionCall {

	private String name;
	private String arguments;

}
