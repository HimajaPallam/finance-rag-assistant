package com.financerag.model;

/**
 * One retrieved chunk cited in an answer, so the user can verify the model
 * against the original filing instead of trusting it blindly.
 */
public class SourceChunk {

    private String documentName;
    private int chunkIndex;
    private double score;
    private String text;

    public SourceChunk(String documentName, int chunkIndex, double score, String text) {
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.score = score;
        this.text = text;
    }

    public String getDocumentName() {
        return documentName;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public double getScore() {
        return score;
    }

    public String getText() {
        return text;
    }
}
