package com.orion.engineering.project.api.payload.response;

import com.orion.engineering_util.api.payload.APIError;
import com.orion.engineering_util.api.payload.APIMeta;
import com.orion.engineering_util.api.payload.APIResponse;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class ProjectSummariesResponse extends APIResponse implements Serializable
{
    private List<Project> projects;


    public ProjectSummariesResponse()
    {
        this(null, null);
    }


    public ProjectSummariesResponse(APIMeta meta, APIError error)
    {
        super(meta == null ? APIMeta.of(UUID.randomUUID()) : meta, error);
    }


    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class Project
    {
        private UUID id;
        private String name;
        private String description;
        private String createdAt;
        private String updatedAt;
    }
}
