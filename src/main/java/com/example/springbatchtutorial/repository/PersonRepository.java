package com.example.springbatchtutorial.repository;

import com.example.springbatchtutorial.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByEmail(String email);

    @Query("select p from Person p where p.jobExecutionId = :jobExecutionId and p.processingStatus <> 'REJECTED'")
    List<Person> findAllByJobExecutionId(@Param("jobExecutionId") Long jobExecutionId);

    void deleteByJobExecutionId(Long jobExecutionId);

    @Modifying
    @Transactional
    @Query("update Person p set p.processingStatus = 'REJECTED' where p.jobExecutionId = :jobExecutionId and p.processingStatus <> 'PROCESSED'")
    int markAllNonProcessedAsRejected(@Param("jobExecutionId") Long jobExecutionId);
} 