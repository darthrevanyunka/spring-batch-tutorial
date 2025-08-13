package com.example.springbatchtutorial.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;

@Component
@Slf4j
public class JobCompletionNotificationListener implements JobExecutionListener {

    @Override
    public void beforeJob(@NonNull JobExecution jobExecution) {
        log.info("\n\n🎬 ========================================");
        log.info("🚀 JOB STARTING: {}", jobExecution.getJobInstance().getJobName());
        log.info("📋 Job Instance ID: {}", jobExecution.getJobInstance().getInstanceId());
        log.info("🆔 Job Execution ID: {}", jobExecution.getId());
        log.info("⏰ Start Time: {}", jobExecution.getStartTime());
        log.info("📊 Job Parameters: {}", jobExecution.getJobParameters());
        log.info("🎯 Expected Steps: {}", jobExecution.getJobInstance().getJobName());
        log.info("========================================\n\n");
    }

    @Override
    public void afterJob(@NonNull JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();
        
        log.info("🏁 ========================================");
        log.info("✅ JOB COMPLETED: {}", jobExecution.getJobInstance().getJobName());
        log.info("📋 Job Instance ID: {}", jobExecution.getJobInstance().getInstanceId());
        log.info("🆔 Job Execution ID: {}", jobExecution.getId());
        log.info("📊 Final Status: {}", jobExecution.getStatus());
        log.info("⏰ Start Time: {}", startTime);
        log.info("⏰ End Time: {}", endTime);
        
        if (startTime != null && endTime != null) {
            Duration duration = Duration.between(startTime, endTime);
            log.info("⏱️ Total Duration: {} ms", duration.toMillis());
        }
        
        log.info("📈 Exit Code: {}", jobExecution.getExitStatus().getExitCode());
        log.info("📝 Exit Description: {}", jobExecution.getExitStatus().getExitDescription());
        
        // Log step execution details
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        log.info("📋 Step Execution Summary:");
        for (StepExecution stepExecution : stepExecutions) {
            log.info("   📝 Step: {} - Status: {} - Read: {} - Write: {} - Skip: {}", 
                    stepExecution.getStepName(),
                    stepExecution.getStatus(),
                    stepExecution.getReadCount(),
                    stepExecution.getWriteCount(),
                    stepExecution.getSkipCount());
            
            if (stepExecution.getStartTime() != null && stepExecution.getEndTime() != null) {
                Duration stepDuration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime());
                log.info("   ⏱️ Step Duration: {} ms", stepDuration.toMillis());
            }
            if (stepExecution.getSkipCount() > 0) {
                log.warn("   ⚠️ {} items were skipped in step {}", stepExecution.getSkipCount(), stepExecution.getStepName());
            }
        }
        
        // Log failure information if job failed
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("❌ JOB FAILED!");
            log.error("🔍 Failure Exceptions:");
            jobExecution.getAllFailureExceptions().forEach(exception -> {
                log.error("   ❌ Exception: {}", exception.getMessage(), exception);
            });
        } else if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("🎉 JOB SUCCESSFULLY COMPLETED!");
        }
        
        log.info("========================================\n\n");
    }
} 