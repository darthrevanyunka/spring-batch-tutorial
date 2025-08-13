package com.example.springbatchtutorial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpringBatchTutorialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBatchTutorialApplication.class, args);
    }
} 