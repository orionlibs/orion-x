package com.orion.engineering.project.sdk;

import com.orion.engineering.project.api.payload.response.ProjectSummariesResponse;
import com.orion.engineering.project.api.payload.response.ProjectsCountResponse;
import feign.HeaderMap;
import feign.Headers;
import feign.RequestLine;
import java.util.Map;

/**
 * Blocking/synchronous Feign client interface.
 * <p>
 * Example usage:
 * <pre>{@code
 * SDKFeignClient client = SDKFeignClientFactory
 *     .builder()
 *     .baseUrl("http://localhost:8080")
 *     .build()
 *     .create();
 * }</pre>
 */
@Headers({
                "Accept: application/json",
                "Content-Type: application/json"
})
public interface EngineeringProjectServiceFeignClient
{
    @RequestLine("GET /projects/summaries")
    ProjectSummariesResponse getProjectsSummaries(@HeaderMap Map<String, String> headers);


    @RequestLine("GET /projects/count")
    ProjectsCountResponse getNumberOfProjects(@HeaderMap Map<String, String> headers);


    //@RequestLine("POST /internal/v1/institutions/{institutionId}/ais/authorization/redirect/init?raw={raw}")
    //APIResponse createProject(CreateProjectRequest request, @HeaderMap Map<String, String> headers);
}
