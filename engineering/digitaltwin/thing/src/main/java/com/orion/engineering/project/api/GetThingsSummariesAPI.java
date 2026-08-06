package com.orion.engineering.project.api;

import com.orion.engineering.project.Things;
import com.orion.engineering.project.api.payload.response.ThingSummariesResponse;
import com.orion.engineering.project.model.ThingModel;
import com.orion.engineering_util.calendar.CalendarUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GetThingsSummariesAPI
{
    @Autowired private Things thingService;


    @GetMapping(value = "/things/summaries")
    public ResponseEntity<ThingSummariesResponse> getThingsSummaries()
    {
        List<ThingModel> things = thingService.getThings();
        List<ThingSummariesResponse.Thing> data = new ArrayList<>();
        for(ThingModel thing : things)
        {
            data.add(ThingSummariesResponse.Thing.builder()
                                                 .id(thing.getId())
                                                 .name(thing.getName())
                                                 .description(thing.getDescription())
                                                 .createdAt(thing.getCreatedAt().format(CalendarUtils.formatter))
                                                 .updatedAt(thing.getUpdatedAt().format(CalendarUtils.formatter))
                                                 .build());
        }
        ThingSummariesResponse response = new ThingSummariesResponse();
        response.setThings(data);
        return ResponseEntity.ok(response);
    }
}
