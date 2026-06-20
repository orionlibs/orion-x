package com.orion.ai.agent.cli;

import com.orion.ai.agent.cli.configuration.OrionConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
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
    @Autowired
    private OrionConfiguration config;


    static void main(String[] args)
    {
        SpringApplication application = new SpringApplication(AgentCLIApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }


    @Bean
    public ApplicationRunner agentRunner()
    {
        return args -> {
            if (args.getNonOptionArgs().isEmpty())
            {
                return;
            }
            String prompt = args.getNonOptionArgs().get(0);
            OrionConfiguration.Openrouter.Api api = config.getOpenrouter().getApi();
            OrionConfiguration.Openrouter.Ai ai = config.getOpenrouter().getAi();
            Agent agent = new Agent(api.getKey(), api.getBaseUrl(), ai.getModelId());
            System.out.println(agent.prompt(prompt));
        };
    }


    @Bean
    public YAMLMapper yamlMapper()
    {
        return YAMLMapper.builder().build();
    }
}
