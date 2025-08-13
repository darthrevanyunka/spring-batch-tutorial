### Spring Batch Tutorial with Advanced Monitoring and Scenarios

This project demonstrates a complete Spring Batch flow with a small frontend for starting jobs, observing progress, and exploring fault-tolerance patterns. It is designed to showcase Spring Batch architecture end-to-end rather than the business logic itself.

## What you’ll learn
- Chunk-oriented processing with Readers, Processors, and Writers
- Multi-step job orchestration
- Fault tolerance: retry and skip
- Passing job parameters and reading them with step-scoped beans
- Exposing monitoring and control endpoints (Actuator + REST)
- A tiny UI that drives jobs and visualizes executions

## At a glance
- Input: CSV of people (`input/persons.csv`)
- Flow: CSV → DB → Age calculation (mock API) → Write file
- Output: `output/persons_with_age.txt`
- Scenarios: SUCCESS, PARTIAL (skips), FAIL (hard fail), RETRYABLE (transient errors)

## Architecture overview

- Job: `processPersonJob` in `BatchConfig`
  - Step 1 `step1SaveToDatabase`: Read CSV and upsert to DB
    - Reader: `csvItemReader()` reads and validates CSV
    - Writer: `jpaItemWriter()` upserts by email (duplicate handling)
  - Step 2 `step2CalculateAge`: Calculate ages (batched) and persist
    - Reader: `databaseItemReader()` loads persons from DB
    - Processor: `ageCalculationProcessor(...)` applies scenario behavior (no API call per item)
    - Chunk size: 5 (demo default; tweakable for performance experiments)
    - Writer: `batchThenUpsertWriter()`
      - Calls one batched mock Age API per chunk via `AgeCalculationService.calculateAgesForPersons(List<Person>)`
      - Then delegates to `UpsertPersonItemWriter` to upsert ages
    - Fault tolerance: `.skip(AgeCalculationSkippableException)` and `.retry(AgeCalculationRetryableException)`
  - Step 3 `step3WriteToFile`: Write results to file
    - Reader: `fileOutputDatabaseReader()` loads persons for output
    - Writer: `fileItemWriter()` writes `firstName,age`

```text
┌──────────────┐    ┌────────────────┐    ┌────────────────────┐    ┌─────────────────┐
│ persons.csv  │ => │ Step 1: DB     │ => │ Step 2: Age+DB     │ => │ Step 3: File     │
│ (CSV Reader) │    │ (JPA Writer)   │    │ (Retry/Skip in P)  │    │ (FlatFile Writer)│
└──────────────┘    └────────────────┘    └────────────────────┘    └─────────────────┘
```

## Project structure (key files)
- `src/main/java/com/example/springbatchtutorial/config/BatchConfig.java`
  - Job and Step definitions; Readers, Processor, Writers; fault tolerance
- `src/main/java/com/example/springbatchtutorial/controller/JobController.java`
  - REST endpoints to start/stop/restart jobs and fetch metrics/executions
- `src/main/java/com/example/springbatchtutorial/service/AgeCalculationService.java`
  - Mock external API for age calculation
- `src/main/java/com/example/springbatchtutorial/service/ScenarioMode.java`
  - Enum: `SUCCESS`, `PARTIAL`, `FAIL`, `RETRYABLE`
- `src/main/java/com/example/springbatchtutorial/exception/*`
  - Custom exceptions to drive skip/retry behavior
- `src/main/resources/static/index.html`
  - Minimal UI for job control and monitoring (uses REST + Actuator)

## Running the app

Prerequisites:
- Java 17+
- Maven 3.6+

Start:
```bash
mvn spring-boot:run
```

UI: open `http://localhost:8080`.

Input CSV location (configurable):
- Default: `input/persons.csv`
- Property: `batch.csv.file.path` in `application.yml`

CSV format:
```csv
firstName,lastName,email,dateOfBirth
John,Doe,john.doe@example.com,1990-01-15
Jane,Smith,jane.smith@example.com,1985-03-22
```

### Sample datasets for demos

Pre-baked CSVs that make scenarios obvious live under `input/samples/`:

- `persons_success.csv` – all valid rows → complete success
- `persons_partial.csv` – intentionally includes invalid rows (bad email, future/empty/unrealistic dates) to show validation filtering in Step 1 and skips in Step 2 if you set `scenario=PARTIAL` and `skipEvery`
- `persons_fail.csv` – a tiny file to pair with `scenario=FAIL` to show immediate failure
- `persons_retryable.csv` – several rows to pair with `scenario=RETRYABLE` and `retryAttempts`

To run a sample:

1) Copy one to the canonical input path:
```bash
cp input/samples/persons_success.csv input/persons.csv
```

2) Start the job with desired scenario from UI or via curl, e.g.:
```bash
curl -X POST http://localhost:8080/api/jobs/start \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"PARTIAL","skipEvery":2}'
```

## Spring Batch flow details

- Step 1: CSV → DB
  - Validates rows (columns present, email format, date sanity checks)
  - Upsert by `email` using `PersonRepository` (no duplicates)

- Step 2: DB → Age (mock) → DB
  - Processor is `@StepScope` and reads job parameters to decide scenario behavior:
    - `scenario`: `SUCCESS | PARTIAL | FAIL | RETRYABLE`
    - `skipEvery`: for PARTIAL, every Nth item throws `AgeCalculationSkippableException` to be skipped
    - `retryAttempts`: for RETRYABLE, each item throws `AgeCalculationRetryableException` up to N attempts, then succeeds
  - Fault tolerance on the step:
    - `.skip(AgeCalculationSkippableException.class).skipLimit(100)`
    - `.retry(AgeCalculationRetryableException.class).retryLimit(3)`

- Step 3: DB → File
  - Writes `output/persons_with_age.txt` with `firstName,age`

## Scenarios (how to drive use cases)

You can run the same job with different behavior using job parameters.

From the UI (`/`):
- Select a Scenario (SUCCESS, PARTIAL, FAIL, RETRYABLE)
- Optional: `skipEvery` (e.g., 3) and `retryAttempts` (e.g., 2)
- Click “Start Job”

From curl:
```bash
# SUCCESS
curl -X POST http://localhost:8080/api/jobs/start \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"SUCCESS"}'

# PARTIAL: skip every 3rd item
curl -X POST http://localhost:8080/api/jobs/start \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"PARTIAL","skipEvery":3}'

# FAIL: hard fail
curl -X POST http://localhost:8080/api/jobs/start \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"FAIL"}'

# RETRYABLE: transient error for each item, succeed after 2 retries
curl -X POST http://localhost:8080/api/jobs/start \
  -H 'Content-Type: application/json' \
  -d '{"scenario":"RETRYABLE","retryAttempts":2}'
```

Parameters are accepted by `JobController` and injected into the processor via `@StepScope` using SpEL:
- `@Value("#{jobParameters['scenario']}") String scenarioParam`
- `@Value("#{jobParameters['skipEvery']}") String skipEveryParam`
- `@Value("#{jobParameters['retryAttempts']}") String retryAttemptsParam`

## Status correlation (job/steps ↔ item statuses)
- Performance demo: API batch size vs Step 2 chunk size

  The mock Age API now supports up to 100 persons per request. Step 2 uses a chunk size of 5 by default so you can observe the effect of changing chunk size in `BatchConfig`.

  How to try:
  1) Open `src/main/java/com/example/springbatchtutorial/config/BatchConfig.java`.
  2) In `step2CalculateAge()`, change the chunk size from `5` to `100`:
     - Before: `<Person, Person>chunk(5, transactionManager)`
     - After:  `<Person, Person>chunk(100, transactionManager)`
  3) Run a larger dataset (e.g., generate from the UI `/api/data/generate/partial10k` then point the CSV path to that file) with `scenario=RETRYABLE` and `retryAttempts=2`.
  4) Compare total duration in the logs and `/api/jobs/executions` between chunk 5 and chunk 100. You should see fewer API calls and improved total time when chunking at 100 due to amortized fixed overhead per call.

  Notes:
  - The mock API simulates a fixed overhead plus a small per-item cost. Batching reduces total calls and thus total fixed overhead.
  - If you set chunk > 100, the writer will split the call into sub-batches of 100 to respect the API’s max batch size.


This section explains how high-level job outcomes relate to per-person `processingStatus` values.

Key item statuses (`Person.processingStatus`):
- IMPORTED: Set in Step 1 for newly inserted rows (via `UpsertPersonItemWriter`).
- PROCESSED: Set when an existing row is updated in Step 2 (age calculated and saved).
- REJECTED: Set in Step 2 SkipListener when an item is skipped during processing.

How statuses evolve by scenario:
- SUCCESS
  - Job/Steps: all steps COMPLETED.
  - Items: Step 1 inserts as IMPORTED (new rows). Step 2 calculates age in batched API calls (size 5) and upserts; affected rows become PROCESSED.
  - Result: all valid items end as PROCESSED with non-null age.

- PARTIAL (e.g., `skipEvery=2`)
  - Job/Steps: Step 2 is COMPLETED with skips; job COMPLETED.
  - Items: Items that trigger skippable errors are marked REJECTED (SkipListener updates DB). Others are PROCESSED. Any invalid CSV rows filtered in Step 1 never reach Step 2 and therefore remain absent from DB.
  - Result: mix of PROCESSED and REJECTED in DB; skip counts visible in execution history.

- RETRYABLE (e.g., `retryAttempts=2`)
  - Job/Steps: Step 2 COMPLETED after in-chunk retries (limit is 3 by default). Job COMPLETED.
  - Items: After transient errors are exhausted, age is calculated and saved; items end as PROCESSED. No REJECTED records (unless retry limit is exceeded).

- FAIL
  - Job/Steps: Step 2 fails immediately; job FAILED.
  - Items: Step 1 may have imported items (IMPORTED). Step 2 halts before upserting ages, so PROCESSED is not set for that run. No REJECTED unless failure is due to skippable errors (not the case for FAIL scenario).

Typical flow of a single record:
1) Step 1 upsert → status IMPORTED (if newly inserted).
2) Step 2 batch-of-5 API call → if successful, writer updates person (sets age) and status becomes PROCESSED.
3) If Step 2 skipped the item (skippable error), SkipListener sets status to REJECTED.

Observability tips:
- Logs show: "External age API batch request - size=5 (one call for entire chunk)" exactly once per chunk in Step 2.
- `/api/jobs/executions` returns per-step skip counts and the writer’s inserted/updated/written counts.
- The frontend Dashboard shows Step 2 chunk size and highlights skips; Execution History lists overall status and duration.

## Monitoring and control

REST endpoints (`JobController`):
- `POST /api/jobs/start` – start a job with optional scenario params
- `POST /api/jobs/stop` – stop running executions
- `POST /api/jobs/restart` – start a new execution (fresh params)
- `GET /api/jobs/status` – quick status
- `GET /api/jobs/executions` – list executions with step summaries
- `GET /api/jobs/metrics` – aggregates: totals, success/fail counts, avg duration

Actuator endpoints (enabled in `application.yml`):
- `GET /actuator/health`
- `GET /actuator/metrics`
- `GET /actuator/batch-jobs`
- `GET /actuator/batch-job-executions`

Frontend (`src/main/resources/static/index.html`):
- Scenario selector + fields for `skipEvery` and `retryAttempts`
- Buttons: Start, Stop, Restart, Refresh
- Live progress bar and per-step status
- Execution history and simple metrics

## Configuration highlights

`src/main/resources/application.yml`:
- H2 in-memory DB, console at `/h2-console`
- Spring Batch auto-start disabled; schema initialization enabled
- Actuator exposure includes batch endpoints
- CSV path: `batch.csv.file.path` (default `input/persons.csv`)

## Testing and regression

Run tests:
```bash
mvn test
```

Included tests:
- `BatchJobIntegrationTest` runs the full job and asserts DB/file outputs

Recommended additions (examples):
- Processor unit tests for scenario logic (SUCCESS, PARTIAL, FAIL, RETRYABLE)
- Step-level tests verifying skip/retry counts and exit statuses
- Controller tests ensuring parameters are passed to job parameters

## Extending the tutorial

- Add a new Step: create new reader/processor/writer and chain via `.next(newStep())`
- Swap input source: implement a different `ItemReader` (e.g., JDBC, REST)
- Enrich processor: call real external services via WebClient/RestTemplate
- Harden fault tolerance: configure backoff, `noRetry`, or write a custom `SkipPolicy`

## Troubleshooting

- CSV not found: ensure `input/persons.csv` exists or set `batch.csv.file.path`
- Duplicate emails: handled by upsert logic in `jpaItemWriter()`
- Step 2 fails immediately: you likely selected `FAIL` scenario
- Many skips: set `PARTIAL` with `skipEvery`; adjust `skipLimit` if needed
- Retries never resolve: increase `retryLimit` or lower `retryAttempts`

## Glossary

- Chunk-oriented processing: read N items, process them, write them in a transaction
- ItemReader/Processor/Writer: core abstractions for each chunk
- Job/Step: a job is composed of ordered steps
- Retry/Skip: fault-tolerance strategies for transient and permanent errors