package com.orion.engineering.project.api.payload.response;

import com.orion.engineering_util.api.payload.APIError;
import com.orion.engineering_util.api.payload.APIMeta;
import com.orion.engineering_util.api.payload.APIResponse;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;

@Data
public class ProjectsCountResponse extends APIResponse implements Serializable
{
    private Long count;


    public ProjectsCountResponse()
    {
        this(null, null);
    }


    public ProjectsCountResponse(APIMeta meta, APIError error)
    {
        super(meta == null ? APIMeta.of(UUID.randomUUID()) : meta, error);
    }
}
