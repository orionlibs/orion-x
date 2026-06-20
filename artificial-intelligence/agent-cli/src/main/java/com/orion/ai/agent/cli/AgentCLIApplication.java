package com.orion.ai.agent.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import tools.jackson.dataformat.yaml.YAMLMapper;

@SpringBootApplication
@ComponentScan(basePackages = {"com.orion"})
public class AgentCLIApplication
{
    static void main(String[] args)
    {
        SpringApplication application = new SpringApplication(AgentCLIApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
        //ProgramParameters parameters = new ProgramParameters(args);
        //Agent agent = new Agent(parameters.getApiKey(), parameters.getBaseUrl());
        //String reply = agent.prompt(parameters.getPrompt());
    }


    @Bean
    public YAMLMapper yamlMapper()
    {
        return YAMLMapper.builder().build();
    }
}
