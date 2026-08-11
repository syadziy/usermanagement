package com.mac.usermanagement.config;

import com.mac.usermanagement.utils.handler.OperationalEventInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    private final OperationalEventInterceptor operationalEventInterceptor;

    public WebMvcConfig(OperationalEventInterceptor operationalEventInterceptor) {
        this.operationalEventInterceptor = operationalEventInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(operationalEventInterceptor).addPathPatterns("/api/v1/auth/login");
    }
}
