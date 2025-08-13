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
  - Step 2 `step2CalculateAge`: Calculate ages and persist
    - Reader: `databaseItemReader()` loads persons from DB
    - Processor: `ageCalculationProcessor(...)` calls a mock Age API and applies scenario behavior
    - Writer: `jpaItemWriter()` updates DB
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