package com.mac.usermanagement.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "usermanagement.registration")
public record RegistrationProperties(boolean enabled) {}
