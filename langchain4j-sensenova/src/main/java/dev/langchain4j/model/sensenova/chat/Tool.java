package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class Tool {

	private final ToolType type;
	private final Function function;

	@SerializedName("web_search")
	private final WebSearch webSearch;
	@SerializedName("associated_knowledge")
	private final Knowledge knowledge;

	public Tool(Function function) {
		this.type = ToolType.FUNCTION;
		this.function = function;
		this.webSearch = null;
		this.knowledge = null;
	}

	public Tool(WebSearch webSearch) {
		this.type = ToolType.WEB_SEARCH;
		this.function = null;
		this.webSearch = webSearch;
		this.knowledge = null;
	}

	public Tool(Knowledge knowledge) {
		this.type = ToolType.ASSOCIATED_KNOWLEDGE;
		this.function = null;
		this.webSearch = null;
		this.knowledge = knowledge;
	}

	public static Tool from(Function function) {
		return new Tool(function);
	}

	public static Tool from(WebSearch webSearch) {
		return new Tool(webSearch);
	}

	public static Tool from(Knowledge knowledge) {
		return new Tool(knowledge);
	}
}
