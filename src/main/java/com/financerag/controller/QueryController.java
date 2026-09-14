package com.financerag.controller;

import com.financerag.model.AskRequest;
import com.financerag.model.AskResponse;
import com.financerag.service.RagQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueryController {

    private final RagQueryService ragQueryService;

    public QueryController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    /**
     * Ask a question against everything ingested so far.
     */
    @PostMapping("/api/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        RagQueryService.AnswerWithSources result = ragQueryService.answer(request.getQuestion());
        return ResponseEntity.ok(new AskResponse(request.getQuestion(), result.answer(), result.sources()));
    }
}
