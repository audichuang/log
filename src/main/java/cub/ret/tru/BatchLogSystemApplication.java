package cub.ret.tru;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

/**
 * Spring Batch 日誌系統主應用程式
 */
@SpringBootApplication
@EnableBatchProcessing
public class BatchLogSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchLogSystemApplication.class, args);
    }
}