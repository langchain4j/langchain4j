package dev.langchain4j.store.embedding.vespa;

import com.google.gson.annotations.SerializedName;
import java.util.List;

class Record {

  private String id;
  private Double relevance;
  private Fields fields;

  public Record(String id, String textSegment, List<Float> vector) {
    this.id = id;
    this.fields = new Fields(textSegment, vector);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

    public Fields(String textSegment, List<Float> vector) {
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
