package com.orion.engineering_util.api.payload;

import java.io.Serializable;
import java.util.List;

public class APIError implements Serializable
{
    private Integer httpStatusCode;
    private List<APIErrorIssue> issues;


    public static APIError of(Integer httpStatusCode, List<APIErrorIssue> issues)
    {
        APIError error = new APIError();
        error.setHttpStatusCode(httpStatusCode);
        error.setIssues(issues);
        return error;
    }


    public Integer getHttpStatusCode()
    {
        return httpStatusCode;
    }


    public void setHttpStatusCode(Integer httpStatusCode)
    {
        this.httpStatusCode = httpStatusCode;
    }


    public List<APIErrorIssue> getIssues()
    {
        return issues;
    }


    public void setIssues(List<APIErrorIssue> issues)
    {
        this.issues = issues;
    }
}
