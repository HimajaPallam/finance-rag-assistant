package com.financerag.model;

/**
 * One retrieved chunk cited in an answer, so the user can verify the model
 * against the original filing instead of trusting it blindly.
 */
public class SourceChunk {

    private String documentName;
    private String companyName;
    private int chunkIndex;
    private double score;
    private String text;

    public SourceChunk(String documentName, String companyName, int chunkIndex, double score, String text) {
        this.documentName = documentName;
        this.companyName = companyName;
        this.chunkIndex = chunkIndex;
        this.score = score;
        this.text = text;
    }

    public String getDocumentName() {
        return documentName;
    }

    /**
     * Which company this chunk was tagged with at ingestion time (see
     * DocumentIngestionService.UNSPECIFIED_COMPANY for the fallback value
     * used when no company was given at upload).
     */
    public String getCompanyName() {
        return companyName;
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
