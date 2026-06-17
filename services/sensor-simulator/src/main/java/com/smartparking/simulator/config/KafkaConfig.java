package com.smartparking.simulator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic spotEventsTopic() {
        return TopicBuilder.name("spot-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
