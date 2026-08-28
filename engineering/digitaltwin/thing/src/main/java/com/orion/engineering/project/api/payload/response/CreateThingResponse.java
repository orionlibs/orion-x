package com.orion.engineering.project.api.payload.response;

import com.orion.engineering.project.model.ThingRegistrationMethod;
import com.orion.engineering.project.model.ThingStatus;
import com.orion.engineering.project.model.ThingType;
import com.orion.engineering_util.api.payload.APIError;
import com.orion.engineering_util.api.payload.APIMeta;
import com.orion.engineering_util.api.payload.APIResponse;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateThingResponse extends APIResponse implements Serializable
{
    private UUID id;
    private String name;
    private String description;
    private ThingType thingType;
    private UUID parentId;
    private String unsTopic;
    private ThingStatus status;
    private ThingRegistrationMethod registrationMethod;
    private String mqttClientId;
    private Map<String, Object> metadata;
    private String createdAt;
    private String updatedAt;


    public CreateThingResponse()
    {
        this(null, null);
    }


    public CreateThingResponse(APIMeta meta, APIError error)
    {
        super(meta == null ? APIMeta.of(UUID.randomUUID()) : meta, error);
    }
}
