package com.orion.util.web;

import com.orion.util.abstraction.AResource;
import java.util.UUID;
import lombok.Data;

@Data
public class WebResource extends AResource
{
    private String uri;


    public WebResource(UUID id, String name, String description, String uri)
    {
        super(id, name, description);
        this.uri = uri;
    }
}
