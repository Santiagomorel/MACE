package com.company.rotations.actionexecutor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sts.StsClient;

@Configuration
public class AwsConfiguration {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Bean
    public StsClient stsClient() {
        return StsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public IamClient iamClient() {
        return IamClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
