package com.financerag.model;

public class AskRequest {

    private String question;

    /**
     * Optional. When set, restricts retrieval to chunks ingested under this
     * exact company name (see IngestController's "company" upload field).
     * When null/blank, the question is answered against every ingested
     * document regardless of company - the original, pre-scoping behavior.
     */
    private String company;

    public AskRequest() {
    }

    public AskRequest(String question) {
        this.question = question;
    }

    public AskRequest(String question, String company) {
        this.question = question;
        this.company = company;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }
}
