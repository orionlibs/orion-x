package com.orion.engineering.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.orion.engineering.project.api.payload.response.ThingSummariesResponse;
import com.orion.engineering.project.model.ThingModel;
import com.orion.engineering.project.model.ThingsDAO;
import com.orion.engineering_util.calendar.CalendarUtils;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

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
class GetThingsSummariesAPITest
{
    @Autowired private TestRestTemplate restTemplate;
    @MockitoBean private ThingsDAO thingsDAO;


    @Test
    @DisplayName("When the database has no things, then the endpoint returns an empty list")
    void testGetThingsSummariesWhenDatabaseEmpty()
    {
        when(thingsDAO.findAll()).thenReturn(List.of());
        ResponseEntity<ThingSummariesResponse> response = restTemplate.getForEntity("/things/summaries", ThingSummariesResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getThings()).isEmpty();
    }


    @Test
    @DisplayName("When the database has things, then the endpoint returns their summaries")
    void testGetThingsSummariesWhenDatabaseHasRows()
    {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-10T10:15:30Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-02-20T11:45:00Z");
        ThingModel thing = newThing(UUID.randomUUID(), "thing-a", "a test thing", createdAt, updatedAt);
        when(thingsDAO.findAll()).thenReturn(List.of(thing));
        ResponseEntity<ThingSummariesResponse> response = restTemplate.getForEntity("/things/summaries", ThingSummariesResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getThings()).hasSize(1);
        ThingSummariesResponse.Thing summary = response.getBody().getThings().get(0);
        assertThat(summary.getId()).isEqualTo(thing.getId());
        assertThat(summary.getName()).isEqualTo(thing.getName());
        assertThat(summary.getDescription()).isEqualTo(thing.getDescription());
        assertThat(summary.getCreatedAt()).isEqualTo(createdAt.format(CalendarUtils.formatter));
        assertThat(summary.getUpdatedAt()).isEqualTo(updatedAt.format(CalendarUtils.formatter));
    }


    private static ThingModel newThing(UUID id, String name, String description, OffsetDateTime createdAt, OffsetDateTime updatedAt)
    {
        ThingModel thing = new ThingModel();
        ReflectionTestUtils.setField(thing, "id", id);
        thing.setName(name);
        thing.setDescription(description);
        ReflectionTestUtils.setField(thing, "createdAt", createdAt);
        ReflectionTestUtils.setField(thing, "updatedAt", updatedAt);
        return thing;
    }
}
