package com.smartparking.penalty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PenaltyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PenaltyServiceApplication.class, args);
    }
}
