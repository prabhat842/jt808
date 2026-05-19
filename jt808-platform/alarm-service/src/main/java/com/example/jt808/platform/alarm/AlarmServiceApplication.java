package com.example.jt808.platform.alarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AlarmProperties.class)
public class AlarmServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlarmServiceApplication.class, args);
    }
}
