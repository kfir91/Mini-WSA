package com.wsa.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IngestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestApiApplication.class, args);
    }
}
