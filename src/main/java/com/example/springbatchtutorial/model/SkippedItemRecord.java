package com.example.springbatchtutorial.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SkippedItemRecord {
    private String email;
    private String firstName;
    private String lastName;
    private String reason;
    private String exceptionType;
}


