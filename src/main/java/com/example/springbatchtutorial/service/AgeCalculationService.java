package com.example.springbatchtutorial.service;

import com.example.springbatchtutorial.model.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

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

    public void calculateAgesForPersons(List<Person> persons) {
        if (persons == null || persons.isEmpty()) {
            return;
        }

        final int maxBatchSize = 100;          // API can handle up to 100 per call
        final long baseOverheadMs = 100;        // fixed network/serialization overhead per call (demo)
        final long perItemCostMs = 5;           // per-person compute cost (demo)

        int total = persons.size();
        int from = 0;
        while (from < total) {
            int to = Math.min(from + maxBatchSize, total);
            List<Person> window = persons.subList(from, to);
            try {
                log.info("🌐 External age API batch request - size={} (one call for entire chunk)", window.size());
                Thread.sleep(baseOverheadMs + perItemCostMs * window.size());

                LocalDate today = LocalDate.now();
                for (Person person : window) {
                    int age = Period.between(person.getDateOfBirth(), today).getYears();
                    person.setAge(age);
                }
                log.debug("✅ Batch API response processed - ages set for {} persons", window.size());
            } catch (InterruptedException e) {
                log.error("❌ Interrupted during batch age calculation: {}", e.getMessage());
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("❌ Error during batch age calculation: {}", e.getMessage(), e);
                return;
            }
            from = to;
        }
    }
} 