window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;


$(document).ready(function()
{
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/run-failures-by-important-repository', analyticsPipelinesForImportantReposPage.buildPipelineRunFailuresByImportantRepoBarChart);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let analyticsPipelinesForImportantReposPage =
{
    buildPipelineRunFailuresByImportantRepoBarChart : function(response)
    {
        let seriesLabels = [];
        let series = [];

        response.data.data.forEach(pairOfRepoAndNumberOfFailures => {
            seriesLabels.push(pairOfRepoAndNumberOfFailures.first);
            series.push(pairOfRepoAndNumberOfFailures.second);
        });

        orionCommon.buildApexCharBarChartWithDataLabels('Pipeline Run Failures by Important Repository', seriesLabels, series, "#pipeline-run-failures-by-important-repo-bar-chart");
    }
};