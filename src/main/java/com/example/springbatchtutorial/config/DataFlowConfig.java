package com.example.springbatchtutorial.config;

import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableTask
public class DataFlowConfig {
    // This enables Spring Cloud Task functionality
    // The @EnableTask annotation allows this application to be deployed as a task
    // in Spring Cloud Data Flow
}
