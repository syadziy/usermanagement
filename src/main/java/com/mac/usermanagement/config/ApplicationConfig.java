package com.mac.usermanagement.config;

import com.mac.usermanagement.config.properties.JwtProperties;
import com.mac.usermanagement.config.properties.RegistrationProperties;
import com.mac.usermanagement.config.properties.AuditPublisherProperties;
import com.mac.usermanagement.config.properties.ErrorAlertProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, RegistrationProperties.class,
        AuditPublisherProperties.class, ErrorAlertProperties.class})
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    HttpClient userManagementHttpClient(ErrorAlertProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
