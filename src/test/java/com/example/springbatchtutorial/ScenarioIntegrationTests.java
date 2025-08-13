package com.example.springbatchtutorial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class ScenarioIntegrationTests {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private com.example.springbatchtutorial.repository.PersonRepository personRepository;

    @BeforeEach
    void setup() throws IOException {
        Files.createDirectories(Path.of("input"));
        Files.createDirectories(Path.of("output"));
    }

    @Test
    void successScenario_usesSuccessCsv_completes() throws Exception {
        copy("input/samples/persons_success.csv", "input/persons.csv");

        JobExecution exec = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("scenario", "SUCCESS")
                .addString("csvPath", "input/persons.csv")
                .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, exec.getStatus());
        assertTrue(Files.exists(Path.of("output/persons_with_age.txt")));
    }

    @Test
    void partialScenario_skipsEvery2_completesWithSkips() throws Exception {
        copy("input/samples/persons_success.csv", "input/persons.csv");

        JobExecution exec = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("scenario", "PARTIAL")
                .addString("skipEvery", "2")
                .addString("csvPath", "input/persons.csv")
                .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, exec.getStatus());
        assertTrue(exec.getStepExecutions().stream().anyMatch(se -> se.getSkipCount() > 0));
    }

    @Test
    void failScenario_failsImmediately() throws Exception {
        copy("input/samples/persons_fail.csv", "input/persons.csv");

        JobExecution exec = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("scenario", "FAIL")
                .addString("csvPath", "input/persons.csv")
                .toJobParameters());

        assertEquals(BatchStatus.FAILED, exec.getStatus());
    }

    @Test
    void retryableScenario_retriesThenSucceeds() throws Exception {
        copy("input/samples/persons_retryable.csv", "input/persons.csv");

        JobExecution exec = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("scenario", "RETRYABLE")
                .addString("retryAttempts", "2")
                .addString("csvPath", "input/persons.csv")
                .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, exec.getStatus());
    }

    @Test
    void editableCsv_fixThenRerun_upsertsFixedPersons() throws Exception {
        // 1) First run with invalid entries present
        copy("input/samples/persons_partial.csv", "input/persons.csv");

        JobExecution first = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("scenario", "SUCCESS")
                .addString("csvPath", "input/persons.csv")
                .toJobParameters());

        assertEquals(BatchStatus.COMPLETED, first.getStatus());

        // Ensure some known-invalid rows were not imported on first run
        assertTrue(personRepository.findByEmail("partial.valid01@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.invalid.email@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.invalid2@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.emptydob@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.emptydob2@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.future@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.future2@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.toolold@example.com").isPresent());
        assertFalse(personRepository.findByEmail("partial.toolold2@example.com").isPresent());

        // 2) Edit the CSV to fix invalid rows
        Path csv = Path.of("input/persons.csv");
        List<String> lines = Files.readAllLines(csv);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("partial.invalid.email.example.com")) {
                lines.set(i, line.replace("partial.invalid.email.example.com", "partial.invalid.email@example.com"));
            }
            if (line.contains("partial.invalid2.example.com")) {
                lines.set(i, line.replace("partial.invalid2.example.com", "partial.invalid2@example.com"));
            }
            if (line.startsWith("PARTIAL_EMPTY_DOB,Demo,partial.emptydob@example.com,")) {
                lines.set(i, "PARTIAL_EMPTY_DOB,Demo,partial.emptydob@example.com,1999-01-01");
            }
            if (line.startsWith("PARTIAL_EMPTY_DOB2,Demo,partial.emptydob2@example.com,")) {
                lines.set(i, "PARTIAL_EMPTY_DOB2,Demo,partial.emptydob2@example.com,1998-02-02");
            }
            if (line.contains(",partial.future@example.com,2999-05-05")) {
                lines.set(i, line.replace("2999-05-05", "1990-05-05"));
            }
            if (line.contains(",partial.future2@example.com,2999-02-14")) {
                lines.set(i, line.replace("2999-02-14", "1991-02-14"));
            }
            if (line.contains(",partial.toolold@example.com,1800-01-01")) {
                lines.set(i, line.replace("1800-01-01", "1980-01-01"));
            }
            if (line.contains(",partial.toolold2@example.com,1800-06-18")) {
                lines.set(i, line.replace("1800-06-18", "1981-06-18"));
            }
        }
        Files.write(csv, lines);

        // 3) Second run: should upsert the newly valid persons
        JobExecution second = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addString("scenario", "SUCCESS")
                .addString("csvPath", "input/persons.csv")
                .toJobParameters());
        assertEquals(BatchStatus.COMPLETED, second.getStatus());

        // All previously invalid emails should now be present
        assertTrue(personRepository.findByEmail("partial.invalid.email@example.com").isPresent());
        assertTrue(personRepository.findByEmail("partial.invalid2@example.com").isPresent());
        assertTrue(personRepository.findByEmail("partial.emptydob@example.com").isPresent());
        assertTrue(personRepository.findByEmail("partial.emptydob2@example.com").isPresent());
        assertTrue(personRepository.findByEmail("partial.future@example.com").isPresent());
        assertTrue(personRepository.findByEmail("partial.future2@example.com").isPresent());
        assertTrue(personRepository.findByEmail("partial.toolold@example.com").isPresent());
        assertTrue(personRepository.findByEmail("partial.toolold2@example.com").isPresent());
    }

    private static void copy(String src, String dest) throws IOException {
        Files.copy(Path.of(src), Path.of(dest), StandardCopyOption.REPLACE_EXISTING);
    }
}


