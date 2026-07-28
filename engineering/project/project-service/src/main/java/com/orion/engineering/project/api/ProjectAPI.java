package com.orion.engineering.project.api;

import com.orion.engineering.project.Projects;
import com.orion.engineering_util.api.payload.APIResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectAPI
{
    @Autowired private Projects projects;


    @GetMapping(value = "/projects/summaries")
    public ResponseEntity<APIResponse> getProjectsSummaries()
    {
        List<RepositoryData> repositories = repositoryService.getRepositoriesNames();
        APIResponse<RepositoriesResponse> response = new APIResponse<>(RepositoriesResponse.builder()
                                                                                           .repositories(repositories)
                                                                                           .build());
        return ResponseEntity.ok(response);
    }


    @GetMapping(value = "/projects/count")
    public ResponseEntity<APIResponse> getNumberOfProjects()
    {
        int numberOfRepositories = repositoryService.getNumberOfRepositories();
        APIResponse<RepositoryCountResponse> response = new APIResponse<>(RepositoryCountResponse.builder()
                                                                                                 .numberOfRepositories(numberOfRepositories)
                                                                                                 .build());
        return ResponseEntity.ok(response);
    }


    @GetMapping(value = "/projects/{projectID}/details")
    public ResponseEntity<APIResponse> getProjectDetails(@PathVariable(name = "projectID") UUID projectID)
    {
        RepositoryData repositoryData = repositoryService.getRepositoryDetails(repositoryName, true, true);
        APIResponse<RepositoryDetailsResponse> response = new APIResponse<>(RepositoryDetailsResponse.builder()
                                                                                                     .repositoryData(repositoryData)
                                                                                                     .build());
        return ResponseEntity.ok(response);
    }
}
