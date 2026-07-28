package com.orion.engineering_util.api.payload;

import java.io.Serializable;
import java.util.UUID;

public class Meta implements Serializable
{
    private UUID traceID;


    public static Meta of()
    {
        return new Meta();
    }


    public static Meta of(UUID traceID)
    {
        Meta meta = new Meta();
        meta.setTraceID(traceID);
        return meta;
    }


    public UUID getTraceID()
    {
        return traceID;
    }


    public void setTraceID(UUID traceID)
    {
        this.traceID = traceID;
    }
}
