package com.orion.engineering.project.api;

import com.orion.engineering.project.Projects;
import com.orion.engineering.project.api.payload.response.ProjectSummariesResponse;
import com.orion.engineering.project.api.payload.response.ProjectsCountResponse;
import com.orion.engineering.project.model.ProjectModel;
import com.orion.engineering_util.api.payload.APIMeta;
import com.orion.engineering_util.api.payload.APIResponse;
import com.orion.engineering_util.calendar.CalendarUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectAPI
{
    @Autowired private Projects projectService;


    @GetMapping(value = "/projects/summaries")
    public ResponseEntity<APIResponse> getProjectsSummaries()
    {
        List<ProjectModel> projects = projectService.getProjects();
        List<ProjectSummariesResponse.Project> data = new ArrayList<>();

        for(ProjectModel project : projects)
        {
            data.add(ProjectSummariesResponse.Project.builder()
                                                     .id(project.getId())
                                                     .name(project.getName())
                                                     .description(project.getDescription())
                                                     .createdAt(project.getCreatedAt().format(CalendarUtils.formatter))
                                                     .updatedAt(project.getUpdatedAt().format(CalendarUtils.formatter))
                                                     .build());
        }

        APIResponse<ProjectSummariesResponse> response = new APIResponse<>(APIMeta.of(UUID.randomUUID()), ProjectSummariesResponse.builder()
                                                                                                                                  .projects(data)
                                                                                                                                  .build());
        return ResponseEntity.ok(response);
    }


    @GetMapping(value = "/projects/count")
    public ResponseEntity<APIResponse> getNumberOfProjects()
    {
        long count = projectService.getNumberProjects();
        APIResponse<ProjectsCountResponse> response = new APIResponse<>(APIMeta.of(UUID.randomUUID()), ProjectsCountResponse.builder()
                                                                                                                            .count(count)
                                                                                                                            .build());
        return ResponseEntity.ok(response);
    }
}
