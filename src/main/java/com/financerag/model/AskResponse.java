package com.financerag.model;

import java.util.List;

public class AskResponse {

    private String question;
    private String company;
    private String answer;
    private List<SourceChunk> sources;

    public AskResponse(String question, String company, String answer, List<SourceChunk> sources) {
        this.question = question;
        this.company = company;
        this.answer = answer;
        this.sources = sources;
    }

    public String getQuestion() {
        return question;
    }

    /**
     * Echoes back the company scope that was actually applied, so a caller
     * can tell "this answer was restricted to Apple" apart from "this answer
     * searched everything" - null/absent means no scoping was requested.
     */
    public String getCompany() {
        return company;
    }

    public String getAnswer() {
        return answer;
    }

    public List<SourceChunk> getSources() {
        return sources;
    }
}
