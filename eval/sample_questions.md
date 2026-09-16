# Sample evaluation questions

A handful of question/expected-answer pairs against `data/sample_filing.txt`
(the fictional "Example Corp" filing). Use these to sanity-check retrieval and
generation quality after any change to chunk size, top-k, or prompt wording -
this is the kind of lightweight eval loop worth mentioning in an interview,
even without a full RAGAS/automated harness.

| # | Question | Expected to appear in answer |
|---|----------|-------------------------------|
| 1 | What were the three reportable segments? | Payments, Lending Technology, Wealth Management Software |
| 2 | What was total revenue and how much did it grow? | $612 million, 14% |
| 3 | What percentage of revenue came from the top ten clients? | 34% |
| 4 | Why did Lending Technology segment revenue decline? | rising interest rates / higher-interest-rate environment |
| 5 | Was the disclosure controls evaluation effective? | Yes / effective |
| 6 | What is the Company's total debt? | No outstanding long-term debt |
| 7 | What is the CEO's name? | Not in context - answer should say the document doesn't contain this |
| 8 | What will the company's revenue look like next year? | Not in context - forward-looking, filing only covers historical data |
| 9 | Who will be the future CEO of the company? | Not in context - no succession information in the filing |

Question 7 is a deliberate negative test: the sample filing never names a CEO,
so a well-grounded system should say it can't find the answer rather than
inventing one. If your model answers this with a specific name, that's a
concrete, demoable example of hallucination to discuss - and a good excuse to
tighten the prompt (e.g. add "If unsure, say you don't know" more forcefully)
or lower `min-score` in application.yml.

Questions 8 and 9 are a harder variant of the same negative test. Unlike
question 7 (where retrieval finds essentially nothing relevant), these two
questions retrieve chunks that are topically close - actual revenue figures,
an actual mention of "the Chief Executive Officer" in the disclosure-controls
section - without actually answering what was asked. This is the more
realistic and more dangerous failure mode for a RAG system: a model can be
tempted to blend real, grounded facts with its own general "trained"
reasoning to produce a plausible-sounding forecast or guess, which looks
grounded (it cites real chunks) while actually smuggling in speculation.

**Verified result (2026-09, `llama3.2` via Ollama, this project's default
prompt/config):** both questions were correctly refused. For question 8, the
model responded that the context only covers the most recent fiscal year's
revenue with no guidance or projections for future years. For question 9, it
responded that the context only mentions the CEO/CFO's role in evaluating
disclosure controls, with no succession or future-CEO information present.
Neither answer speculated - a good sign that the prompt's grounding
instruction holds up even on a near-miss retrieval, not just on a total miss.

If you change the prompt wording, the model, or the retrieval settings later,
re-run questions 8 and 9 as a regression check - a model or config change
that causes either of these to start speculating would be a real quality
regression worth catching before considering this system trustworthy again.

A good follow-up stress test, not yet verified: *"Based on the interest rate
sensitivity mentioned, what do you predict revenue growth will be next
year?"* - this explicitly invites the model to reason forward from a real,
stated fact, which is a subtler trap than a plain "what will happen"
question. It tests whether the model can tell the difference between
reporting a fact and extrapolating beyond it.

## Running these by hand

```bash
curl -X POST http://localhost:8080/api/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "What were the three reportable segments?"}'
```

## Ideas for making this a real automated eval (good next step to mention)

- A small JUnit test that POSTs each question and asserts the expected
  keyword appears (case-insensitive) in the response's `answer` field.
- Track retrieval quality separately from generation quality: assert the
  *sources* returned include the chunk that actually contains the answer,
  independent of whether the LLM phrased the final answer well.
- Swap in RAGAS (Python) or a Java equivalent for faithfulness/relevance
  scoring once you want to go beyond keyword matching.
