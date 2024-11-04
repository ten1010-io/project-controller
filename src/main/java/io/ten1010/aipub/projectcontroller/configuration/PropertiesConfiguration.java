package io.ten1010.aipub.projectcontroller.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({K8sProperties.class, AipubProperties.class})
public class PropertiesConfiguration {
}
