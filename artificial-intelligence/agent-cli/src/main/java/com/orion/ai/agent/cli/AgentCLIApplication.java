package com.orion.ai.agent.cli;

import com.orion.ai.agent.cli.configuration.Config;
import com.orion.ai.agent.cli.configuration.OrionConfiguration;
import com.orion.ai.agent.cli.core.Agent;
import com.orion.ai.agent.cli.core.Session;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ApplicationRunner agentRunner()
    {
        return args -> {
            Config.config = config;
            Session.sessionID = UUID.randomUUID();
            Agent.SELECTED_AGENT = config.getAi().getAgents().get(config.getAi().getDefaultAgent());
        };
    }


    @Bean
    public YAMLMapper yamlMapper()
    {
        return YAMLMapper.builder().build();
    }
}
