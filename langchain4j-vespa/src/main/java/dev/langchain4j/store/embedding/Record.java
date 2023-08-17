package dev.langchain4j.store.embedding;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Record {

  private Double relevance;
  private Fields fields;

  public Record(String documentId, String textSegment, List<Float> vector) {
    this.fields = new Fields(documentId, textSegment, vector);
  }

  public double getRelevance() {
    return relevance;
  }

  public void setRelevance(double relevance) {
    this.relevance = relevance;
  }

  public Fields getFields() {
    return fields;
  }

  public void setFields(Fields fields) {
    this.fields = fields;
  }

  public static class Fields {

    @SerializedName("documentid")
    private String documentId;
    @SerializedName("text_segment")
    private String textSegment;
    private Vector vector;

    public Fields(String documentId, String textSegment, List<Float> vector) {
      this.documentId = documentId;
      this.textSegment = textSegment;
      this.vector = new Vector(vector);
    }

    public String getDocumentId() {
      return documentId;
    }

    public void setDocumentId(String documentId) {
      this.documentId = documentId;
    }

    public String getTextSegment() {
      return textSegment;
    }

    public void setTextSegment(String textSegment) {
      this.textSegment = textSegment;
    }

    public Vector getVector() {
      return vector;
    }

    public void setVector(Vector vector) {
      this.vector = vector;
    }

    public static class Vector {

      private List<Float> values;

      public Vector(List<Float> values) {
        this.values = values;
      }

      public List<Float> getValues() {
        return values;
      }

      public void setValues(List<Float> values) {
        this.values = values;
      }
    }
  }
}
