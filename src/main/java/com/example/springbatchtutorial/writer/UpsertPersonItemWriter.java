package com.example.springbatchtutorial.writer;

import com.example.springbatchtutorial.model.Person;
import com.example.springbatchtutorial.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.Chunk;
import org.springframework.lang.NonNull;
import com.example.springbatchtutorial.model.ProcessingStatus;


@RequiredArgsConstructor
@Slf4j
public class UpsertPersonItemWriter implements ItemWriter<Person>, StepExecutionListener {

    private final PersonRepository personRepository;

    private int insertedCount;
    private int updatedCount;
    private int writtenCount;

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        this.insertedCount = 0;
        this.updatedCount = 0;
        this.writtenCount = 0;
        if (stepExecution.getJobExecution() != null) {
            this.currentJobExecutionId = stepExecution.getJobExecution().getId();
        }
    }

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        stepExecution.getExecutionContext().putInt("inserted.count", insertedCount);
        stepExecution.getExecutionContext().putInt("updated.count", updatedCount);
        stepExecution.getExecutionContext().putInt("written.count", writtenCount);
        log.info("   📦 Upsert summary - inserted: {}, updated: {}, written: {}", insertedCount, updatedCount, writtenCount);
        return ExitStatus.COMPLETED;
    }

    private Long currentJobExecutionId;

    @Override
    public void write(@NonNull Chunk<? extends Person> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        for (Person person : chunk) {
            if (currentJobExecutionId != null) {
                person.setJobExecutionId(currentJobExecutionId);
            }
            var existing = personRepository.findByEmail(person.getEmail());
            if (existing.isPresent()) {
                Person existingPerson = existing.get();
                existingPerson.setFirstName(person.getFirstName());
                existingPerson.setLastName(person.getLastName());
                existingPerson.setDateOfBirth(person.getDateOfBirth());
                existingPerson.setAge(person.getAge());
                existingPerson.setJobExecutionId(currentJobExecutionId);
                existingPerson.setProcessingStatus(ProcessingStatus.PROCESSED);
                personRepository.save(existingPerson);
                updatedCount++;
            } else {
                person.setProcessingStatus(ProcessingStatus.IMPORTED);
                personRepository.save(person);
                insertedCount++;
            }
            writtenCount++;
        }
    }
}


