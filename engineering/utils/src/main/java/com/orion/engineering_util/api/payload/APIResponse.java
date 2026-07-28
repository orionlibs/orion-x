package com.orion.engineering_util.api.payload;

import java.io.Serializable;

public class APIResponse implements Serializable
{
    private APIMeta meta;
    private APIError error;


    public APIResponse(APIMeta meta, APIError error)
    {
        this.meta = meta;
        this.error = error;
    }


    public APIMeta getMeta()
    {
        return meta;
    }


    public void setMeta(APIMeta meta)
    {
        this.meta = meta;
    }


    public APIError getError()
    {
        return error;
    }


    public void setError(APIError error)
    {
        this.error = error;
    }
}
