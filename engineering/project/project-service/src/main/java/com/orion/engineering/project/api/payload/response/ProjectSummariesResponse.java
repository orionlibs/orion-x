package com.orion.engineering.project.api.payload.response;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ProjectSummariesResponse implements Serializable
{
    private List<Project> projects;


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
