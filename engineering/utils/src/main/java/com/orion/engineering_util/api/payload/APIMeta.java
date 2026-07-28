package com.orion.engineering_util.api.payload;

import java.io.Serializable;
import java.util.UUID;

public class APIMeta implements Serializable
{
    private UUID traceID;


    public static APIMeta of()
    {
        return new APIMeta();
    }


    public static APIMeta of(UUID traceID)
    {
        APIMeta meta = new APIMeta();
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
