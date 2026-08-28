package com.orion.engineering.project.api;

import com.orion.engineering.project.Things;
import com.orion.engineering.project.api.payload.request.CreateThingRequest;
import com.orion.engineering.project.api.payload.response.CreateThingResponse;
import com.orion.engineering.project.model.ThingModel;
import com.orion.engineering_util.calendar.CalendarUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CreateThingAPI
{
    @Autowired private Things thingService;


    @PostMapping(value = "/things")
    public ResponseEntity<CreateThingResponse> createThing(@RequestBody CreateThingRequest request)
    {
        ThingModel thing = new ThingModel();
        thing.setName(request.getName());
        thing.setDescription(request.getDescription());
        thing.setThingType(request.getThingType());
        thing.setParentId(request.getParentId());
        thing.setUnsTopic(request.getUnsTopic());
        thing.setStatus(request.getStatus());
        thing.setRegistrationMethod(request.getRegistrationMethod());
        thing.setMqttClientId(request.getMqttClientId());
        thing.setMetadata(request.getMetadata());
        ThingModel createdThing = thingService.createThing(thing);
        CreateThingResponse response = new CreateThingResponse();
        response.setId(createdThing.getId());
        response.setName(createdThing.getName());
        response.setDescription(createdThing.getDescription());
        response.setThingType(createdThing.getThingType());
        response.setParentId(createdThing.getParentId());
        response.setUnsTopic(createdThing.getUnsTopic());
        response.setStatus(createdThing.getStatus());
        response.setRegistrationMethod(createdThing.getRegistrationMethod());
        response.setMqttClientId(createdThing.getMqttClientId());
        response.setMetadata(createdThing.getMetadata());
        response.setCreatedAt(createdThing.getCreatedAt().format(CalendarUtils.formatter));
        response.setUpdatedAt(createdThing.getUpdatedAt().format(CalendarUtils.formatter));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
