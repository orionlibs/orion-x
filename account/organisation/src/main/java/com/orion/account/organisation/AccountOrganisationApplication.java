package com.orion.account.organisation;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootApplication
@EnableWebMvc
@ComponentScan(basePackages = {"com.orion"})
public class AccountOrganisationApplication extends SpringBootServletInitializer implements WebMvcConfigurer
{
    static void main(String[] args)
    {
        SpringApplication.run(AccountOrganisationApplication.class, args);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }


    @Bean
    public HandlerMapping handlerMapping() {
        return new RequestMappingHandlerMapping();
    }


    @Bean
    public HandlerAdapter handlerAdapter() {
        return new RequestMappingHandlerAdapter();
    }


    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "websocket", "ws")
                .allowedHeaders("*");
    }
}
