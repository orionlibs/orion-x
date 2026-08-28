package com.orion.engineering.project.api.payload.request;

import com.orion.engineering.project.model.ThingRegistrationMethod;
import com.orion.engineering.project.model.ThingStatus;
import com.orion.engineering.project.model.ThingType;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateThingRequest implements Serializable
{
    private String name;
    private String description;
    private ThingType thingType;
    private UUID parentId;
    private String unsTopic;
    private ThingStatus status;
    private ThingRegistrationMethod registrationMethod;
    private String mqttClientId;
    private Map<String, Object> metadata;
}
