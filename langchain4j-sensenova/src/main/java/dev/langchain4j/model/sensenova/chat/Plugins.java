package dev.langchain4j.model.sensenova.chat;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Plugins {

	@SerializedName("web_search")
	private WebSearch webSearch;

	@SerializedName("associated_knowledge")
	private Knowledge knowledge;

}
