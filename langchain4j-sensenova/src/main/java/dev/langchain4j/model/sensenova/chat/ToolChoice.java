package dev.langchain4j.model.sensenova.chat;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.model.sensenova.chat.ToolType.FUNCTION;


@ToString
@EqualsAndHashCode
public class ToolChoice {

	private final ToolType type = FUNCTION;
	private final Function function;

	public ToolChoice(String functionName) {
		this.function = Function.builder().name(functionName).build();
	}

	public static ToolChoice from(String functionName) {
		return new ToolChoice(functionName);
	}
}