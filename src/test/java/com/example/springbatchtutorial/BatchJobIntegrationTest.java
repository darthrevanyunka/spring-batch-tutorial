package com.example.springbatchtutorial;

import com.example.springbatchtutorial.model.Person;
import com.example.springbatchtutorial.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class BatchJobIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private PersonRepository personRepository;

    @BeforeEach
    void setUp() throws IOException {
        // Create input directory and test CSV file
        createTestCsvFile();
        
        // Create output directory
        createOutputDirectory();
        
        // Clear database
        personRepository.deleteAll();
    }

    @Test
    void testCompleteBatchJob() throws Exception {
        // Run the job
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();
        
        // Verify job completed successfully
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
        
        // Verify data was saved to database
        long personCount = personRepository.count();
        assertTrue(personCount > 0, "Persons should be saved to database");
        
        // Verify persons have age calculated
        long personsWithAge = personRepository.findAll().stream()
                .filter(p -> p.getAge() != null)
                .count();
        assertEquals(personCount, personsWithAge, "All persons should have age calculated");
        
        // Print database content for verification
        personRepository.findAll().forEach(person -> 
            System.out.println("Person: " + person.getFirstName() + ", Age: " + person.getAge()));
        
        // Verify output file was created (basic check)
        Path outputFile = Paths.get("output/persons_with_age.txt");
        assertTrue(Files.exists(outputFile), "Output file should be created");
        
        // Log the output file content for debugging
        String content = Files.readString(outputFile);
        System.out.println("Output file content: " + content);
        System.out.println("Output file size: " + content.length());
    }

    private void createTestCsvFile() throws IOException {
        // Create input directory
        File inputDir = new File("input");
        if (!inputDir.exists()) {
            inputDir.mkdirs();
        }
        
        // Create test CSV file
        File csvFile = new File("input/persons.csv");
        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("FirstName,LastName,Email,DateOfBirth\n");
            writer.write("John,Doe,john.doe@example.com,1998-05-15\n");
            writer.write("Jane,Smith,jane.smith@example.com,1993-08-22\n");
            writer.write("Bob,Johnson,bob.johnson@example.com,1985-12-10\n");
            writer.write("Alice,Brown,alice.brown@example.com,1990-03-28\n");
            writer.write("Charlie,Wilson,charlie.wilson@example.com,1988-11-05\n");
        }
    }

    private void createOutputDirectory() throws IOException {
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }
} 