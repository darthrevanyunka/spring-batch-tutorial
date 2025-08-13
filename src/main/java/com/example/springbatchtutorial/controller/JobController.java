package com.example.springbatchtutorial.controller;

import com.example.springbatchtutorial.model.Person;
import com.example.springbatchtutorial.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.Duration;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobLauncher jobLauncher;
    private final Job processPersonJob;
    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;
    private final PersonRepository personRepository;
    
    @GetMapping("/data/generate/partial10k")
    public ResponseEntity<String> generateLargePartialCsv() {
        try {
            File dir = new File("input/samples");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, "persons_partial_10k.csv");
            try (PrintWriter pw = new PrintWriter(file)) {
                pw.println("FirstName,LastName,Email,DateOfBirth");
                // Create 10k rows with every 7th having an invalid email to trigger skips
                for (int i = 1; i <= 10000; i++) {
                    String first = String.format("PARTIAL10K_%05d", i);
                    String last = "Demo";
                    String email = (i % 7 == 0) ? ("invalid-" + i + ".example.com") : ("partial10k" + i + "@example.com");
                    String dob = String.format("%04d-%02d-%02d", 1980 + (i % 30), ((i % 12) + 1), ((i % 28) + 1));
                    pw.println(first + "," + last + "," + email + "," + dob);
                }
            }
            return ResponseEntity.ok("Generated: " + file.getPath());
        } catch (Exception e) {
            log.error("Failed generating 10k partial CSV", e);
            return ResponseEntity.internalServerError().body("Failed generating file: " + e.getMessage());
        }
    }

    @PostMapping("/jobs/start")
    public ResponseEntity<String> startJob(@RequestBody(required = false) Map<String, Object> body) {
        log.info("🚀 Received request to start batch job");
        try {
            String scenario = body != null && body.get("scenario") != null ? body.get("scenario").toString() : "SUCCESS";
            String skipEvery = body != null && body.get("skipEvery") != null ? body.get("skipEvery").toString() : null;
            String retryAttempts = body != null && body.get("retryAttempts") != null ? body.get("retryAttempts").toString() : null;
            String csvPath = body != null && body.get("csvPath") != null ? body.get("csvPath").toString() : null;

            JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                    .addString("time", LocalDateTime.now().toString())
                    .addString("scenario", scenario);

            if (skipEvery != null && !skipEvery.isBlank()) {
                paramsBuilder.addString("skipEvery", skipEvery, true);
            }
            if (retryAttempts != null && !retryAttempts.isBlank()) {
                paramsBuilder.addString("retryAttempts", retryAttempts, true);
            }
            if (csvPath != null && !csvPath.isBlank()) {
                paramsBuilder.addString("csvPath", csvPath, true);
            }

            JobParameters jobParameters = paramsBuilder.toJobParameters();
            
            log.info("📋 Job parameters created: {}", jobParameters);
            log.info("🎯 Launching job: processPersonJob");
            
            JobExecution jobExecution = jobLauncher.run(processPersonJob, jobParameters);
            
            log.info("✅ Job started successfully");
            log.info("   - Execution ID: {}", jobExecution.getId());
            log.info("   - Job Instance ID: {}", jobExecution.getJobInstance().getInstanceId());
            log.info("   - Status: {}", jobExecution.getStatus());
            log.info("   - Start Time: {}", jobExecution.getStartTime());
            
            return ResponseEntity.ok("Job started successfully with execution ID: " + jobExecution.getId());
        } catch (JobExecutionAlreadyRunningException | JobRestartException | 
                 JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
            log.error("❌ Error starting job: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error starting job: " + e.getMessage());
        }
    }

    @PostMapping("/jobs/stop")
    public ResponseEntity<String> stopJob() {
        log.info("⏹️ Received request to stop running jobs");
        try {
            // Find running job executions
            Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions("processPersonJob");
            
            log.info("🔍 Found {} running job execution(s)", runningExecutions.size());
            
            if (runningExecutions.isEmpty()) {
                log.info("ℹ️ No running jobs found to stop");
                return ResponseEntity.ok("No running jobs found to stop");
            }
            
            for (JobExecution execution : runningExecutions) {
                log.info("🛑 Stopping job execution ID: {} (Status: {})", execution.getId(), execution.getStatus());
                jobOperator.stop(execution.getId());
                log.info("✅ Successfully stopped job execution: {}", execution.getId());
            }
            
            log.info("🎉 Stopped {} running job(s)", runningExecutions.size());
            return ResponseEntity.ok("Stopped " + runningExecutions.size() + " running job(s)");
        } catch (Exception e) {
            log.error("❌ Error stopping job: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error stopping job: " + e.getMessage());
        }
    }

    @PostMapping("/jobs/restart")
    public ResponseEntity<String> restartJob() {
        log.info("🔄 Received request to restart job");
        try {
            // For restart, we'll just start a new job with a new timestamp
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("restartTime", LocalDateTime.now().toString())
                    .toJobParameters();
            
            log.info("📋 Restart job parameters created: {}", jobParameters);
            log.info("🎯 Launching restarted job: processPersonJob");
            
            JobExecution restartedExecution = jobLauncher.run(processPersonJob, jobParameters);
            
            log.info("✅ Job restarted successfully");
            log.info("   - Execution ID: {}", restartedExecution.getId());
            log.info("   - Status: {}", restartedExecution.getStatus());
            log.info("   - Start Time: {}", restartedExecution.getStartTime());
            
            return ResponseEntity.ok("Job restarted successfully with execution ID: " + restartedExecution.getId());
        } catch (Exception e) {
            log.error("❌ Error restarting job: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error restarting job: " + e.getMessage());
        }
    }

    @GetMapping("/jobs/status")
    public ResponseEntity<String> getJobStatus() {
        log.debug("📊 Received request for job status");
        try {
            Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions("processPersonJob");
            
            log.debug("🔍 Found {} running job execution(s)", runningExecutions.size());
            
            StringBuilder status = new StringBuilder();
            status.append("Job Controller Status: Active\n");
            status.append("Running jobs: ").append(runningExecutions.size());
            
            if (!runningExecutions.isEmpty()) {
                JobExecution latest = runningExecutions.iterator().next();
                status.append("\nLatest running execution ID: ").append(latest.getId());
                status.append("\nStatus: ").append(latest.getStatus());
                
                log.debug("📈 Status details - Execution ID: {}, Status: {}", latest.getId(), latest.getStatus());
            }
            
            String statusMessage = status.toString();
            log.debug("📤 Returning status: {}", statusMessage);
            return ResponseEntity.ok(statusMessage);
        } catch (Exception e) {
            log.error("❌ Error getting job status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error getting status: " + e.getMessage());
        }
    }

    @GetMapping("/persons")
    public ResponseEntity<List<Person>> getAllPersons() {
        log.info("👥 Received request to get all persons");
        try {
            List<Person> persons = personRepository.findAll();
            log.info("📊 Retrieved {} persons from database", persons.size());
            
            if (log.isDebugEnabled()) {
                persons.forEach(person -> log.debug("👤 Person: {} {} ({}) - Age: {}", 
                    person.getFirstName(), person.getLastName(), person.getEmail(), person.getAge()));
            }
            
            return ResponseEntity.ok(persons);
        } catch (Exception e) {
            log.error("❌ Error getting persons: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/persons/count")
    public ResponseEntity<Long> getPersonCount() {
        log.debug("🔢 Received request for person count");
        try {
            long count = personRepository.count();
            log.info("📊 Total persons in database: {}", count);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            log.error("❌ Error getting person count: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/jobs/executions")
    public ResponseEntity<List<Map<String, Object>>> getJobExecutions() {
        log.debug("📋 Received request for job executions");
        try {
            List<Map<String, Object>> executions = new ArrayList<>();
            
            // Get all job instances
            List<JobInstance> jobInstances = jobExplorer.getJobInstances("processPersonJob", 0, 100);
            
            for (JobInstance jobInstance : jobInstances) {
                List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(jobInstance);
                
                for (JobExecution jobExecution : jobExecutions) {
                    Map<String, Object> execution = new HashMap<>();
                    execution.put("id", jobExecution.getId());
                    execution.put("status", jobExecution.getStatus().toString());
                    execution.put("startTime", jobExecution.getStartTime());
                    execution.put("endTime", jobExecution.getEndTime());
                    execution.put("duration", jobExecution.getEndTime() != null && jobExecution.getStartTime() != null ? 
                        java.time.Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis() : null);
                    execution.put("exitCode", jobExecution.getExitStatus() != null ? jobExecution.getExitStatus().getExitCode() : null);
                    
                    // Add step executions with skip info
                    List<Map<String, Object>> stepExecutions = new ArrayList<>();
                    for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
                        Map<String, Object> step = new HashMap<>();
                        step.put("stepName", stepExecution.getStepName());
                        step.put("status", stepExecution.getStatus().toString());
                        step.put("readCount", stepExecution.getReadCount());
                        step.put("writeCount", stepExecution.getWriteCount());
                        step.put("skipCount", stepExecution.getSkipCount());
                        step.put("startTime", stepExecution.getStartTime());
                        step.put("endTime", stepExecution.getEndTime());
                        // Upsert summary counts from writer
                        step.put("inserted", stepExecution.getExecutionContext().getInt("inserted.count", 0));
                        step.put("updated", stepExecution.getExecutionContext().getInt("updated.count", 0));
                        int written = stepExecution.getExecutionContext().containsKey("written.count")
                                ? stepExecution.getExecutionContext().getInt("written.count")
                                : (int) stepExecution.getWriteCount();
                        step.put("written", written);
                        // Extract a concise skip summary from execution context if present
                        String skipSummary = stepExecution.getExecutionContext().containsKey("skip.summary")
                                ? stepExecution.getExecutionContext().getString("skip.summary")
                                : null;
                        if (skipSummary != null) {
                            step.put("skipSummary", skipSummary);
                        }
                        stepExecutions.add(step);
                    }
                    execution.put("stepExecutions", stepExecutions);
                    
                    executions.add(execution);
                }
            }
            
            // Sort by start time (newest first)
            executions.sort((a, b) -> {
                LocalDateTime aTime = (LocalDateTime) a.get("startTime");
                LocalDateTime bTime = (LocalDateTime) b.get("startTime");
                return bTime.compareTo(aTime);
            });
            
            log.info("📊 Retrieved {} job executions", executions.size());
            return ResponseEntity.ok(executions);
        } catch (Exception e) {
            log.error("❌ Error getting job executions: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/jobs/metrics")
    public ResponseEntity<Map<String, Object>> getJobMetrics() {
        log.debug("📈 Received request for job metrics");
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // Get all job executions
            List<JobInstance> jobInstances = jobExplorer.getJobInstances("processPersonJob", 0, 100);
            List<JobExecution> allExecutions = new ArrayList<>();
            
            for (JobInstance jobInstance : jobInstances) {
                allExecutions.addAll(jobExplorer.getJobExecutions(jobInstance));
            }
            
            // Calculate metrics
            long totalExecutions = allExecutions.size();
            long successfulExecutions = allExecutions.stream()
                .filter(e -> e.getStatus() == BatchStatus.COMPLETED)
                .count();
            long failedExecutions = allExecutions.stream()
                .filter(e -> e.getStatus() == BatchStatus.FAILED)
                .count();
            
            // Calculate average duration
            double avgDuration = allExecutions.stream()
                .filter(e -> e.getEndTime() != null && e.getStartTime() != null)
                    .mapToLong(e -> Duration.between(e.getStartTime(), e.getEndTime()).toMillis())
                .average()
                .orElse(0.0);
            
            metrics.put("totalExecutions", totalExecutions);
            metrics.put("successfulExecutions", successfulExecutions);
            metrics.put("failedExecutions", failedExecutions);
            metrics.put("avgDuration", Math.round(avgDuration));
            metrics.put("successRate", totalExecutions > 0 ? (double) successfulExecutions / totalExecutions * 100 : 0);
            
            log.info("📊 Retrieved job metrics: {}", metrics);
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("❌ Error getting job metrics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 