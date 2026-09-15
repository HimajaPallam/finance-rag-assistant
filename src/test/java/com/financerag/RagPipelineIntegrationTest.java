package com.financerag;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "finance-rag.vector-store.persist-path=./data/test-vector-store.json"
)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagPipelineIntegrationTest {

    private static final String TEST_STORE_PATH = "./data/test-vector-store.json";

    @LocalServerPort
    private int port;

    private final TestRestTemplate restTemplate = new TestRestTemplate(
            new RestTemplateBuilder()
                    .setConnectTimeout(Duration.ofSeconds(30))
                    .setReadTimeout(Duration.ofMinutes(1))
    );

    @BeforeAll
    static void wipeTestVectorStore() {
        File testStore = new File(TEST_STORE_PATH);
        if (testStore.exists()) {
            testStore.delete();
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @Order(1)
    @DisplayName("Sample filing ingests successfully")
    void ingestSampleFiling() {
        FileSystemResource resource = new FileSystemResource("data/sample_filing.txt");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/documents", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        int chunksIndexed = ((Number) response.getBody().get("chunksIndexed")).intValue();
        assertTrue(chunksIndexed > 0, "Expected at least one chunk to be indexed");
    }

    @Test
    @Order(2)
    @DisplayName("Positive: reportable segments are correctly identified")
    void answersReportableSegments() {
        String answer = ask("What were the three reportable segments?");
        assertAll(
                () -> assertContainsIgnoreCase(answer, "Payments"),
                () -> assertContainsIgnoreCase(answer, "Lending Technology"),
                () -> assertContainsIgnoreCase(answer, "Wealth Management")
        );
    }

    @Test
    @Order(3)
    @DisplayName("Positive: total revenue and growth are correctly reported")
    void answersRevenueGrowth() {
        String answer = ask("What was total revenue and how much did it grow?");
        assertAll(
                () -> assertContainsIgnoreCase(answer, "612"),
                () -> assertContainsIgnoreCase(answer, "14")
        );
    }

    @Test
    @Order(4)
    @DisplayName("Positive: top-ten client concentration is correctly reported")
    void answersClientConcentration() {
        String answer = ask("What percentage of revenue came from the top ten clients?");
        assertContainsIgnoreCase(answer, "34");
    }

    @Test
    @Order(5)
    @DisplayName("Positive: disclosure controls conclusion is correctly reported")
    void answersDisclosureControls() {
        String answer = ask("Was the disclosure controls evaluation effective?");
        assertContainsIgnoreCase(answer, "effective");
    }

    @Test
    @Order(6)
    @DisplayName("Negative: does not fabricate a CEO name")
    void refusesToNameCeo() {
        String answer = ask("What is the CEO's name?");
        assertDoesNotSpeculate(answer);
    }

    @Test
    @Order(7)
    @DisplayName("Negative: does not fabricate a future revenue forecast")
    void refusesToForecastRevenue() {
        String answer = ask("What will the company's revenue look like next year?");
        assertDoesNotSpeculate(answer);
    }

    @Test
    @Order(8)
    @DisplayName("Negative: does not fabricate future CEO succession information")
    void refusesToNameFutureCeo() {
        String answer = ask("Who will be the future CEO of the company?");
        assertDoesNotSpeculate(answer);
    }

    // --- helpers ---

    private String ask(String question) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("question", question), headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl() + "/api/ask", request, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        String answer = (String) response.getBody().get("answer");
        assertNotNull(answer, "Answer should not be null");
        return answer;
    }

    private void assertContainsIgnoreCase(String haystack, String needle) {
        assertTrue(haystack.toLowerCase().contains(needle.toLowerCase()),
                () -> "Expected answer to contain \"" + needle + "\" but was:\n" + haystack);
    }

    /**
     * A loose heuristic, not a guarantee: checks the answer contains one of a
     * few common "I don't know" phrasings, as a signal the model declined to
     * speculate rather than fabricating an answer. LLM phrasing varies run to
     * run, so this is intentionally permissive. If this starts failing after
     * a prompt or model change, read the actual answer text in the assertion
     * message before assuming the pipeline is broken - it might just be
     * phrased slightly differently than the strings checked for here.
     */
    private void assertDoesNotSpeculate(String answer) {
        String lower = answer.toLowerCase();
        boolean declinesToAnswer = lower.contains("does not contain")
                || lower.contains("doesn't contain")
                || lower.contains("no information")
                || lower.contains("not provide")
                || lower.contains("does not provide")
                || lower.contains("cannot determine")
                || lower.contains("can't determine")
                || lower.contains("unable to")
                || lower.contains("not mentioned")
                || lower.contains("not specified");
        assertTrue(declinesToAnswer,
                () -> "Expected the model to decline answering (no grounding exists for this question), but got:\n" + answer);
    }
}
