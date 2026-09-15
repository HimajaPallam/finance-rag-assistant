package com.financerag.model;

import java.util.List;

public class AskResponse {

    private String question;
    private String answer;
    private List<SourceChunk> sources;

    public AskResponse(String question, String answer, List<SourceChunk> sources) {
        this.question = question;
        this.answer = answer;
        this.sources = sources;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public List<SourceChunk> getSources() {
        return sources;
    }
}
