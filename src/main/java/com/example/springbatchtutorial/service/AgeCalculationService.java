package com.example.springbatchtutorial.service;

import com.example.springbatchtutorial.model.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

@Service
@Slf4j
public class AgeCalculationService {

    public Person calculateAgeForPerson(Person person) {
        log.debug("🧮 Starting age calculation for: {} {} (DOB: {})", 
                 person.getFirstName(), person.getLastName(), person.getDateOfBirth());
        
        try {
            // Simulate external API call with delay
            log.debug("🌐 Making API call to external age calculation service...");
            Thread.sleep(50); // Simulate network delay (snappier for demos)
            
            LocalDate today = LocalDate.now();
            int age = Period.between(person.getDateOfBirth(), today).getYears();
            
            log.debug("✅ API response received - Age calculated: {} years old", age);
            
            person.setAge(age);
            
            log.debug("💾 Updated person record with calculated age: {} {} = {} years old", 
                     person.getFirstName(), person.getLastName(), age);
            
            return person;
            
        } catch (InterruptedException e) {
            log.error("❌ Interrupted during age calculation for {} {}: {}", 
                     person.getFirstName(), person.getLastName(), e.getMessage());
            Thread.currentThread().interrupt();
            return person;
        } catch (Exception e) {
            log.error("❌ Error calculating age for {} {}: {}", 
                     person.getFirstName(), person.getLastName(), e.getMessage(), e);
            return person;
        }
    }
} 