package dev.langchain4j.model.sensenova.chat;


import com.google.gson.annotations.SerializedName;
import lombok.Data;


@Data
public class WebSearchResult {

	private int index;
	private String url;
	@SerializedName("url_source")
	private String urlSource;
	private String title;
	private String icon;
}
