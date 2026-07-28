package com.orion.engineering.project.sdk;

import com.orion.engineering_util.api.payload.APIResponse;
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
public interface SDKFeignClient
{
    @RequestLine("GET /projects/summaries")
    APIResponse getProjectsSummaries(@HeaderMap Map<String, String> headers);


    @RequestLine("GET /projects/count")
    APIResponse getNumberOfProjects(@HeaderMap Map<String, String> headers);


    //@RequestLine("POST /internal/v1/institutions/{institutionId}/ais/authorization/redirect/init?raw={raw}")
    //APIResponse createProject(CreateProjectRequest request, @HeaderMap Map<String, String> headers);
}
