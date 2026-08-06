package com.orion.engineering.project.api;

import com.orion.engineering.project.Things;
import com.orion.engineering.project.api.payload.response.ThingCountResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GetThingsCountAPI
{
    @Autowired private Things thingService;


    @GetMapping(value = "/things/count")
    public ResponseEntity<ThingCountResponse> getNumberOfThings()
    {
        long count = thingService.getNumberOfThings();
        ThingCountResponse response = new ThingCountResponse();
        response.setCount(count);
        return ResponseEntity.ok(response);
    }
}
