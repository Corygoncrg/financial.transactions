package com.example.financial.transactions.config;

import com.example.financial.transactions.Service.TransactionCsvService;
import com.example.financial.transactions.Service.LineMapperService;
import com.example.financial.transactions.model.TransactionCsv;
import com.example.financial.transactions.model.TransactionCsvRecord;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

import java.time.LocalDateTime;

import static com.example.financial.transactions.Service.SqlService.*;

@Configuration
@EnableBatchProcessing
public class SpringBatchConfig {

    @Autowired
    private LineMapperService lineMapperService;

    @Autowired
    private TransactionCsvService csvService;

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<TransactionCsvRecord> reader(@Value("#{jobParameters[filename]}") String filename) {
        return new FlatFileItemReaderBuilder<TransactionCsvRecord>()
                .name("transactionItemReader")
                .resource(new FileSystemResource("upload-dir/" + filename))
                .delimited()
                .names(listFields)
                .targetType(TransactionCsvRecord.class)
                .lineMapper(lineMapperService.lineMapper())
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<TransactionCsvRecord, TransactionCsv> processor(@Value("#{stepExecution.jobExecution.createTime}") LocalDateTime importDate) {
        return csvRecord -> csvService.processRecord(csvRecord, importDate);
    }

    @Bean
    public JdbcBatchItemWriter<TransactionCsv> writer(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransactionCsv>()
                .sql("INSERT INTO transactions (" + allFields + ") VALUES (" + allJavaFields +")")
                .dataSource(dataSource)
                .beanMapped()
                .build();
    }

    @Bean
    public Job importTransactionJob(JobRepository repository, Step step1, JobCompletionNotificationListener listener) {
        return new JobBuilder("importTransactionJob", repository)
                .listener(listener)
                .start(step1)
                .build();
    }

    @Bean
    public Step step1(JobRepository jobRepository,
                      DataSourceTransactionManager transactionManager,
                      FlatFileItemReader<TransactionCsvRecord> reader,
                      JdbcBatchItemWriter<TransactionCsv> writer,
                      ItemProcessor<TransactionCsvRecord, TransactionCsv> processor) {
        return new StepBuilder("step1", jobRepository)
                .<TransactionCsvRecord, TransactionCsv>chunk(3, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
