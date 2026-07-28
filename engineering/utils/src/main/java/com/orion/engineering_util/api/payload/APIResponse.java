package com.orion.engineering_util.api.payload;

import java.io.Serializable;

public class APIResponse<DATA> implements Serializable
{
    private APIMeta meta;
    private APIError error;
    private DATA data;


    public APIResponse(APIMeta meta, APIError error)
    {
        this.meta = meta;
        this.error = error;
    }


    public APIResponse(APIMeta meta, DATA data)
    {
        this.meta = meta;
        this.data = data;
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


    public DATA getData()
    {
        return data;
    }


    public void setData(DATA data)
    {
        this.data = data;
    }
}
