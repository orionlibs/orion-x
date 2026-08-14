package com.orion.engineering.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.orion.engineering.project.api.payload.response.ThingCountResponse;
import com.orion.engineering.project.model.ThingsDAO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@AutoConfigureTestRestTemplate
@SpringBootTest(
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {
                                "spring.autoconfigure.exclude="
                                                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                                                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                                                + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                                                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                                                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
                })
class GetThingsCountAPITest
{
    @Autowired private TestRestTemplate restTemplate;
    @MockitoBean private ThingsDAO thingsDAO;


    @Test
    @DisplayName("When the database has things, then the endpoint returns the count from the repository")
    void testGetNumberOfThingsWhenDatabaseHasRows()
    {
        when(thingsDAO.count()).thenReturn(7L);
        ResponseEntity<ThingCountResponse> response = restTemplate.getForEntity("/things/count", ThingCountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCount()).isEqualTo(7L);
    }


    @Test
    @DisplayName("When the database is empty, then the endpoint returns a count of zero")
    void testGetNumberOfThingsWhenDatabaseEmpty()
    {
        when(thingsDAO.count()).thenReturn(0L);
        ResponseEntity<ThingCountResponse> response = restTemplate.getForEntity("/things/count", ThingCountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCount()).isEqualTo(0L);
    }
}
