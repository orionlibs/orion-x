package com.orion.engineering_util.api.payload;

import java.io.Serializable;

public class APIErrorIssue implements Serializable
{
    private Integer code;
    private String message;


    public static APIErrorIssue of(Integer code, String message)
    {
        APIErrorIssue error = new APIErrorIssue();
        error.setCode(code);
        error.setMessage(message);
        return error;
    }


    public Integer getCode()
    {
        return code;
    }


    public void setCode(Integer code)
    {
        this.code = code;
    }


    public String getMessage()
    {
        return message;
    }


    public void setMessage(String message)
    {
        this.message = message;
    }
}
