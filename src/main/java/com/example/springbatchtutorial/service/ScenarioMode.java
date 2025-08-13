package com.example.springbatchtutorial.service;

public enum ScenarioMode {
    SUCCESS,
    PARTIAL, // some items fail (skipped)
    FAIL,    // hard fail
    RETRYABLE // transient errors then succeed
}


