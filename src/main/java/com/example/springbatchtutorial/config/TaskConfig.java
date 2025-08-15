package com.example.springbatchtutorial.config;

import com.example.springbatchtutorial.SpringBatchTutorialApplication;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableTask
public class TaskConfig {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job processPersonJob;

    @Autowired
    private Environment environment;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            // This will be executed when the task is launched
            System.out.println("🚀 Starting Spring Batch Task...");
            
            // Build job parameters from environment variables (SCDF properties)
            JobParametersBuilder paramsBuilder = new JobParametersBuilder()
                    .addString("time", System.currentTimeMillis() + "")
                    .addString("scenario", environment.getProperty("scenario", "SUCCESS"))
                    .addString("csvPath", environment.getProperty("csvPath", "input/persons.csv"));
            
            // Add optional parameters
            String skipEvery = environment.getProperty("skipEvery");
            if (skipEvery != null && !skipEvery.isEmpty()) {
                paramsBuilder.addString("skipEvery", skipEvery, true);
            }
            
            String retryAttempts = environment.getProperty("retryAttempts");
            if (retryAttempts != null && !retryAttempts.isEmpty()) {
                paramsBuilder.addString("retryAttempts", retryAttempts, true);
            }
            
            JobParameters jobParameters = paramsBuilder.toJobParameters();
            
            System.out.println("📋 Job parameters: " + jobParameters);
            
            // Launch the job
            jobLauncher.run(processPersonJob, jobParameters);
            
            System.out.println("✅ Spring Batch Task completed successfully");
        };
    }
}
