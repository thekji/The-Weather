package com.the.weather.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsConfig {

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${aws.region}") String region) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    S3Presigner s3Presigner(@Value("${aws.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
