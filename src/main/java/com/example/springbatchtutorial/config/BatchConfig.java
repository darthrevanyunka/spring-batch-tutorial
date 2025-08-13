package com.example.springbatchtutorial.config;

import com.example.springbatchtutorial.listener.JobCompletionNotificationListener;
import com.example.springbatchtutorial.model.Person;
import com.example.springbatchtutorial.repository.PersonRepository;
import com.example.springbatchtutorial.writer.UpsertPersonItemWriter;
import com.example.springbatchtutorial.service.AgeCalculationService;
import com.example.springbatchtutorial.service.ScenarioMode;
import com.example.springbatchtutorial.exception.AgeCalculationSkippableException;
import com.example.springbatchtutorial.exception.AgeCalculationRetryableException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
// removed unused imports
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
// removed unused imports
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
// removed unused imports

import org.springframework.lang.NonNull;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.time.LocalDate;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.SkipListener;
import com.example.springbatchtutorial.model.ProcessingStatus;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BatchConfig {
    
    @Value("${batch.csv.file.path:input/persons.csv}")
    private String csvFilePath;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PersonRepository personRepository;
    private final AgeCalculationService ageCalculationService;
    private final JobCompletionNotificationListener jobCompletionNotificationListener;

    // Step-scoped bean proxies injected by name
    @Autowired @Lazy @Qualifier("csvItemReader")
    private ItemReader<Person> csvItemReader;
    @Autowired @Lazy @Qualifier("databaseItemReader")
    private ItemReader<Person> databaseItemReader;
    @Autowired @Lazy @Qualifier("fileOutputDatabaseReader")
    private ItemReader<Person> fileOutputDatabaseReader;
    @Autowired @Lazy @Qualifier("ageCalculationProcessor")
    private ItemProcessor<Person, Person> ageCalculationProcessor;
    @Autowired @Lazy @Qualifier("jpaItemWriter")
    private ItemWriter<Person> jpaItemWriter;
    @Autowired @Lazy @Qualifier("fileItemWriter")
    private FlatFileItemWriter<Person> flatFileItemWriter;

    @Bean
    public Job processPersonJob() {
        log.info("🔧 Creating Spring Batch job: processPersonJob");
        return new JobBuilder("processPersonJob", jobRepository)
                .listener(jobCompletionNotificationListener)
                .start(step1SaveToDatabase())
                .next(step2CalculateAge())
                .next(step3WriteToFile())
                .build();
    }

    @Bean
    public Step step1SaveToDatabase() {
        log.info("📝 Configuring Step 1: Save CSV data to database");
        return new StepBuilder("step1SaveToDatabase", jobRepository)
                .<Person, Person>chunk(100, transactionManager)
                .reader(csvItemReader)
                .writer(upsertPersonItemWriter())
                .listener((StepExecutionListener) upsertPersonItemWriter())
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(@NonNull StepExecution stepExecution) {
                        log.info("🚀 Starting Step 1: Reading CSV and saving to database");
                        log.info("   - Chunk size: 100");
                        log.info("   - Step name: {}", stepExecution.getStepName());
                    }

                    @Override
                    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
                        log.info("✅ Completed Step 1: Save to database");
                        log.info("   - Read count: {}", stepExecution.getReadCount());
                        log.info("   - Write count: {}", stepExecution.getWriteCount());
                        log.info("   - Skip count: {}", stepExecution.getSkipCount());
                        if (stepExecution.getEndTime() != null && stepExecution.getStartTime() != null) {
                            long duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
                            log.info("   - Duration: {}ms", duration);
                        }
                        
                        // Add validation summary to step execution context
                        if (stepExecution.getReadCount() == 0) {
                            log.warn("⚠️ No valid records were processed. This might indicate CSV validation issues.");
                            stepExecution.getExecutionContext().put("validation.warning", "No valid records processed");
                        }
                        
                        return ExitStatus.COMPLETED;
                    }
                })
                .build();
    }

    @Bean
    public Step step2CalculateAge() {
        log.info("🧮 Configuring Step 2: Calculate age for all persons");
        return new StepBuilder("step2CalculateAge", jobRepository)
                .<Person, Person>chunk(500, transactionManager)
                .reader(databaseItemReader)
                .processor(ageCalculationProcessor)
                .writer(batchThenUpsertWriter())
                .listener((StepExecutionListener) upsertPersonItemWriter())
                .faultTolerant()
                .skip(AgeCalculationSkippableException.class)
                .skipLimit(100)
                .retry(AgeCalculationRetryableException.class)
                .retryLimit(3)
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(@NonNull StepExecution stepExecution) {
                        log.info("🚀 Starting Step 2: Calculating ages via API");
                        log.info("   - Chunk size: 5");
                        log.info("   - Step name: {}", stepExecution.getStepName());
                    }

                    @Override
                    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
                        log.info("✅ Completed Step 2: Age calculation");
                        log.info("   - Read count: {}", stepExecution.getReadCount());
                        log.info("   - Write count: {}", stepExecution.getWriteCount());
                        log.info("   - Skip count: {}", stepExecution.getSkipCount());
                        if (stepExecution.getEndTime() != null && stepExecution.getStartTime() != null) {
                            long duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
                            log.info("   - Duration: {}ms", duration);
                        }
                        if (stepExecution.getSkipCount() > 0) {
                            String summary = String.format("Skipped %d items during processing (see logs for item details)", stepExecution.getSkipCount());
                            stepExecution.getExecutionContext().put("skip.summary", summary);
                            log.warn("   ⚠️ {}", summary);
                        }
                        return ExitStatus.COMPLETED;
                    }
                })
                .listener(new SkipListener<Person, Person>() {
                    @Override
                    public void onSkipInProcess(@NonNull Person item, @NonNull Throwable t) {
                        String reason = t != null ? t.getMessage() : "Unknown";
                        log.warn("⚠️ Skipped during processing: {} {} ({}). Reason: {}", item.getFirstName(), item.getLastName(), item.getEmail(), reason);
                        // Mark the item as REJECTED so future processes can avoid it
                        try {
                            var existing = personRepository.findByEmail(item.getEmail());
                            existing.ifPresent(p -> {
                                p.setProcessingStatus(ProcessingStatus.REJECTED);
                                personRepository.save(p);
                            });
                        } catch (Exception ignore) { }
                    }

                    @Override
                    public void onSkipInRead(@NonNull Throwable t) {
                        log.warn("⚠️ Skipped during read. Reason: {}", t != null ? t.getMessage() : "Unknown");
                    }

                    @Override
                    public void onSkipInWrite(@NonNull Person item, @NonNull Throwable t) {
                        String reason = t != null ? t.getMessage() : "Unknown";
                        log.warn("⚠️ Skipped during write: {} {} ({}). Reason: {}", item.getFirstName(), item.getLastName(), item.getEmail(), reason);
                    }
                })
                .build();
    }

    @Bean
    public ItemWriter<Person> batchThenUpsertWriter() {
        return items -> {
            if (items == null || items.isEmpty()) {
                return;
            }
            java.util.List<Person> batch = new java.util.ArrayList<>(items.size());
            for (Person p : items) {
                batch.add(p);
            }
            // One API call per chunk (size 5) to calculate ages for all persons in the chunk
            ageCalculationService.calculateAgesForPersons(batch);
            // Then upsert to DB using the same chunk
            upsertPersonItemWriter().write(items);
        };
    }

    @Bean
    public Step step3WriteToFile() {
        log.info("📄 Configuring Step 3: Write results to output file");
        return new StepBuilder("step3WriteToFile", jobRepository)
                .<Person, Person>chunk(10, transactionManager)
                .reader(fileOutputDatabaseReader)
                .writer(flatFileItemWriter)
                .listener(new StepExecutionListener() {
                    @Override
                    public void beforeStep(@NonNull StepExecution stepExecution) {
                        log.info("🚀 Starting Step 3: Writing results to file");
                        log.info("   - Chunk size: 10");
                        log.info("   - Output file: output/persons_with_age.txt");
                        log.info("   - Step name: {}", stepExecution.getStepName());
                    }

                    @Override
                    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
                        log.info("✅ Completed Step 3: Write to file");
                        log.info("   - Read count: {}", stepExecution.getReadCount());
                        log.info("   - Write count: {}", stepExecution.getWriteCount());
                        log.info("   - Skip count: {}", stepExecution.getSkipCount());
                        if (stepExecution.getEndTime() != null && stepExecution.getStartTime() != null) {
                            long duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis();
                            log.info("   - Duration: {}ms", duration);
                        }
                        return ExitStatus.COMPLETED;
                    }
                })
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<Person> csvItemReader(@Value("#{jobParameters['csvPath']}") String csvPathParam) {
        log.info("📖 Creating CSV item reader");
        return new ItemReader<Person>() {
            private List<Person> persons;
            private int currentIndex = 0;

            @Override
            public Person read() {
                if (persons == null) {
                    log.info("🔄 Initializing CSV reader - loading data from file");
                    persons = readCsvFile();
                    log.info("📊 Loaded {} persons from CSV file", persons.size());
                }
                
                if (currentIndex < persons.size()) {
                    Person person = persons.get(currentIndex++);
                    log.debug("📖 Reading person {}: {} {} ({})", currentIndex, person.getFirstName(), person.getLastName(), person.getEmail());
                    return person;
                }
                log.info("🏁 Finished reading CSV data - processed {} persons", persons.size());
                return null;
            }

            private List<Person> readCsvFile() {
                List<Person> personList = new ArrayList<>();
                
                // Try the configured path first, then fallback to common locations
                List<String> paths = new ArrayList<>();
                if (csvPathParam != null && !csvPathParam.isBlank()) {
                    paths.add(csvPathParam);
                }
                paths.add(csvFilePath);  // configured default
                paths.add("input/persons.csv");
                paths.add("persons.csv");
                paths.add("data/persons.csv");
                paths.add("csv/persons.csv");
                
                String actualFilePath = null;
                File file = null;
                
                // Find the first existing file
                for (String path : paths) {
                    file = new File(path);
                    if (file.exists() && file.length() > 0) {
                        actualFilePath = path;
                        break;
                    }
                }
                
                if (actualFilePath == null) {
                    log.error("❌ CSV file not found in any of the expected locations:");
                    for (String path : paths) {
                        log.error("   - {}", path);
                    }
                    throw new RuntimeException("CSV file not found. Please ensure persons.csv exists in one of the expected locations.");
                }
                
                log.info("📂 Reading CSV file: {}", actualFilePath);
                log.info("📄 File exists, size: {} bytes", (file != null ? file.length() : 0));
                
                int totalLines = 0;
                int validLines = 0;
                int invalidLines = 0;
                List<String> validationErrors = new ArrayList<>();
                
                try (CSVReader reader = new CSVReader(new FileReader(actualFilePath))) {
                    // Skip header
                    String[] header = reader.readNext();
                    log.info("📋 CSV Header: {}", String.join(", ", header));
                    
                    String[] line;
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    int lineNumber = 1;
                    
                    while ((line = reader.readNext()) != null) {
                        lineNumber++;
                        totalLines++;
                        
                        // Validate line structure
                        if (line.length < 4) {
                            String error = String.format("Line %d: Insufficient columns (expected 4, got %d)", lineNumber, line.length);
                            validationErrors.add(error);
                            log.warn("⚠️ {}", error);
                            invalidLines++;
                            continue;
                        }
                        
                        // Validate individual fields
                        String firstName = line[0] != null ? line[0].trim() : "";
                        String lastName = line[1] != null ? line[1].trim() : "";
                        String email = line[2] != null ? line[2].trim() : "";
                        String dateOfBirthStr = line[3] != null ? line[3].trim() : "";
                        
                        // Check for empty required fields
                        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || dateOfBirthStr.isEmpty()) {
                            String error = String.format("Line %d: Empty required field - FirstName: '%s', LastName: '%s', Email: '%s', DateOfBirth: '%s'", 
                                lineNumber, firstName, lastName, email, dateOfBirthStr);
                            validationErrors.add(error);
                            log.warn("⚠️ {}", error);
                            invalidLines++;
                            continue;
                        }
                        
                        // Validate email format (basic validation)
                        if (!email.contains("@")) {
                            String error = String.format("Line %d: Invalid email format: %s", lineNumber, email);
                            validationErrors.add(error);
                            log.warn("⚠️ {}", error);
                            invalidLines++;
                            continue;
                        }
                        
                        // Validate and parse date
                        try {
                            LocalDate dateOfBirth = LocalDate.parse(dateOfBirthStr, formatter);
                            
                            // Validate date is not in the future
                            if (dateOfBirth.isAfter(LocalDate.now())) {
                                String error = String.format("Line %d: Date of birth is in the future: %s", lineNumber, dateOfBirthStr);
                                validationErrors.add(error);
                                log.warn("⚠️ {}", error);
                                invalidLines++;
                                continue;
                            }
                            
                            // Validate reasonable age (not older than 150 years)
                            if (dateOfBirth.isBefore(LocalDate.now().minusYears(150))) {
                                String error = String.format("Line %d: Date of birth seems unrealistic: %s", lineNumber, dateOfBirthStr);
                                validationErrors.add(error);
                                log.warn("⚠️ {}", error);
                                invalidLines++;
                                continue;
                            }
                            
                            // Create person object
                            Person person = new Person(firstName, lastName, email, dateOfBirth);
                            personList.add(person);
                            validLines++;
                            log.debug("✅ Parsed line {}: {} {} ({})", lineNumber, person.getFirstName(), person.getLastName(), person.getEmail());
                            
                        } catch (DateTimeParseException e) {
                            String error = String.format("Line %d: Invalid date format '%s'. Expected format: yyyy-MM-dd", lineNumber, dateOfBirthStr);
                            validationErrors.add(error);
                            log.warn("⚠️ {} - Error: {}", error, e.getMessage());
                            invalidLines++;
                        } catch (Exception e) {
                            String error = String.format("Line %d: Unexpected error parsing line: %s", lineNumber, String.join(",", line));
                            validationErrors.add(error);
                            log.warn("⚠️ {} - Error: {}", error, e.getMessage());
                            invalidLines++;
                        }
                    }
                } catch (IOException | CsvValidationException e) {
                    String error = String.format("❌ Error reading CSV file: %s", e.getMessage());
                    validationErrors.add(error);
                    log.error(error, e);
                }
                
                // Log comprehensive summary
                log.info("📊 CSV Processing Summary:");
                log.info("   - Total lines processed: {}", totalLines);
                log.info("   - Valid records: {}", validLines);
                log.info("   - Invalid records: {}", invalidLines);
                log.info("   - Success rate: {}%", totalLines > 0 ? Math.round((double) validLines / totalLines * 100) : 0);
                
                if (!validationErrors.isEmpty()) {
                    log.warn("⚠️ Validation Errors Summary:");
                    validationErrors.forEach(error -> log.warn("   - {}", error));
                }
                
                if (validLines == 0) {
                    log.error("❌ No valid records found in CSV file. Job will fail.");
                    throw new RuntimeException("No valid records found in CSV file. Please check the input data.");
                }
                
                log.info("📊 Successfully parsed {} persons from CSV file", personList.size());
                return personList;
            }
        };
    }

    @Bean
    @StepScope
    public ItemReader<Person> databaseItemReader(@Value("#{stepExecution.jobExecution.id}") Long jobExecutionId) {
        log.info("🗄️ Creating database item reader");
        return new ItemReader<Person>() {
            private List<Person> persons;
            private int currentIndex = 0;

            @Override
            public Person read() {
                if (persons == null) {
                    log.info("🔄 Initializing database reader - loading all persons");
                    if (jobExecutionId != null) {
                        persons = personRepository.findAllByJobExecutionId(jobExecutionId);
                        log.info("📊 Loaded {} persons from database for execution {}", persons.size(), jobExecutionId);
                    } else {
                        persons = personRepository.findAll();
                        log.info("📊 Loaded {} persons from database (no execution filter)", persons.size());
                    }
                }
                
                if (currentIndex < persons.size()) {
                    Person person = persons.get(currentIndex++);
                    log.debug("🗄️ Reading person {} from DB: {} {} (Age: {})", currentIndex, person.getFirstName(), person.getLastName(), person.getAge());
                    return person;
                }
                log.info("🏁 Finished reading from database - processed {} persons", persons.size());
                return null;
            }
        };
    }

    @Bean
    @StepScope
    public ItemReader<Person> fileOutputDatabaseReader(@Value("#{stepExecution.jobExecution.id}") Long jobExecutionId) {
        log.info("📄 Creating database item reader for file output");
        return new ItemReader<Person>() {
            private List<Person> persons;
            private int currentIndex = 0;

            @Override
            public Person read() {
                if (persons == null) {
                    log.info("🔄 Initializing file output database reader - loading all persons");
                    List<Person> base = (jobExecutionId != null)
                            ? personRepository.findAllByJobExecutionId(jobExecutionId)
                            : personRepository.findAll();
                    persons = base.stream().filter(p -> p.getAge() != null).collect(Collectors.toList());
                    log.info("📊 Loaded {} persons from database for file output (exec={}, age != null)", persons.size(), jobExecutionId);
                }
                
                if (currentIndex < persons.size()) {
                    Person person = persons.get(currentIndex++);
                    log.debug("📄 Reading person {} for file output: {} {} (Age: {})", currentIndex, person.getFirstName(), person.getLastName(), person.getAge());
                    return person;
                }
                log.info("🏁 Finished reading from database for file output - processed {} persons", persons.size());
                return null;
            }
        };
    }

    @Bean
    @StepScope
    public ItemProcessor<Person, Person> ageCalculationProcessor(
            @Value("#{jobParameters['scenario']}") String scenarioParam,
            @Value("#{jobParameters['skipEvery']}") String skipEveryParam,
            @Value("#{jobParameters['retryAttempts']}") String retryAttemptsParam
    ) {
        log.info("🧮 Creating age calculation processor");

        final ScenarioMode scenario = scenarioParam != null ? ScenarioMode.valueOf(scenarioParam) : ScenarioMode.SUCCESS;
        final int skipEvery = skipEveryParam != null ? Integer.parseInt(skipEveryParam) : 0;
        final int retryAttempts = retryAttemptsParam != null ? Integer.parseInt(retryAttemptsParam) : 2;

        final AtomicInteger processedCounter = new AtomicInteger(0);
        final ConcurrentHashMap<String, Integer> emailToAttempts = new ConcurrentHashMap<>();
        final AtomicInteger apiItemCounter = new AtomicInteger(0); // counts actual API calls to log batches of 5

        return person -> {
            log.debug("🧮 Processing age for: {} {} ({})", person.getFirstName(), person.getLastName(), person.getEmail());

            switch (scenario) {
                case FAIL -> {
                    throw new RuntimeException("Simulated hard failure for demonstration");
                }
                case PARTIAL -> {
                    int index = processedCounter.incrementAndGet();
                    if (skipEvery > 0 && index % skipEvery == 0) {
                        throw new AgeCalculationSkippableException("Simulated skippable error at item index " + index);
                    }
                }
                case RETRYABLE -> {
                    String key = person.getEmail();
                    int attempts = emailToAttempts.merge(key, 1, Integer::sum);
                    if (attempts <= retryAttempts) {
                        throw new AgeCalculationRetryableException("Simulated transient error attempt " + attempts + " for " + key);
                    }
                }
                case SUCCESS -> {
                    // fall through to success behaviour
                }
            }

            // No per-item API call anymore; processor only handles scenario logic and returns person as-is.
            Person updatedPerson = person;
            // Age is calculated in the batch writer (one API call per chunk). Keep processor logs minimal.
            log.debug("⏭️ Age will be calculated in batch writer for {} {}", updatedPerson.getFirstName(), updatedPerson.getLastName());
            return updatedPerson;
        };
    }

    @Bean
    public ItemWriter<Person> jpaItemWriter() {
        log.info("💾 Creating JPA item writer with duplicate handling");
        return items -> {
            log.debug("💾 Writing chunk of {} items to database", items.size());
            
            for (Person person : items) {
                // Check if person with same email already exists
                var existingPersonOpt = personRepository.findByEmail(person.getEmail());
                
                if (existingPersonOpt.isPresent()) {
                    // Update existing person
                    Person existingPerson = existingPersonOpt.get();
                    log.debug("🔄 Updating existing person: {} {} ({})", existingPerson.getFirstName(), existingPerson.getLastName(), existingPerson.getEmail());
                    
                    existingPerson.setFirstName(person.getFirstName());
                    existingPerson.setLastName(person.getLastName());
                    existingPerson.setDateOfBirth(person.getDateOfBirth());
                    existingPerson.setAge(person.getAge());
                    
                    personRepository.save(existingPerson);
                    log.debug("✅ Updated existing person with email: {}", person.getEmail());
                } else {
                    // Save new person
                    log.debug("➕ Saving new person: {} {} ({})", person.getFirstName(), person.getLastName(), person.getEmail());
                    personRepository.save(person);
                    log.debug("✅ Saved new person with email: {}", person.getEmail());
                }
            }
            
            log.debug("💾 Completed writing chunk of {} items to database", items.size());
        };
    }

    @Bean
    public FlatFileItemWriter<Person> fileItemWriter() {
        log.info("📄 Creating flat file item writer");
        // Ensure output directory exists for demo friendliness
        File outDir = new File("output");
        if (!outDir.exists() && outDir.mkdirs()) {
            log.info("📁 Created output directory at {}", outDir.getAbsolutePath());
        }
        return new FlatFileItemWriterBuilder<Person>()
                .name("personFileWriter")
                .resource(new FileSystemResource("output/persons_with_age.txt"))
                .lineAggregator(item -> {
                    String line = item.getFirstName() + "," + item.getAge();
                    log.debug("📝 Writing line to file: {}", line);
                    return line;
                })
                .shouldDeleteIfExists(true)
                .build();
    }

    @Bean
    @StepScope
    public UpsertPersonItemWriter upsertPersonItemWriter() {
        return new UpsertPersonItemWriter(personRepository);
    }
} 