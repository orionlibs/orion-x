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
    orionCommon.makeGetAJAXCall('/api/repositories/pipelines/commands/tests/executions/count', analyticsPipelinesPage.showNumberOfTestRuns);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/mean-time-to-repair', analyticsPipelinesPage.showMeanTimeToRepair);
    orionCommon.makeGetAJAXCall('/api/blames/summary?numberOfDays=30', analyticsPipelinesPage.showPeopleToBlame);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/fixed?numberOfDays=30', analyticsPipelinesPage.showFixedPipelineRunsByBlamedPerson);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/fixed-and-total', analyticsPipelinesPage.showNumberOfFixedAndTotalFailedTestRuns);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/runs-by-command', analyticsPipelinesPage.buildPipelineRunsByCommandBarChart);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/run-failures-by-command', analyticsPipelinesPage.buildPipelineRunFailuresByCommandBarChart);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/average-run-duration-by-command', analyticsPipelinesPage.buildAveragePipelineRunDurationByCommandColumnChart);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/failure-rates-per-day', analyticsPipelinesPage.buildPipelineFailureRatesPerDayOfRunsLineDatetimeChart);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/runs-per-day', analyticsPipelinesPage.buildPipelineRunsLineDatetimeChart);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let analyticsPipelinesPage =
{
    showNumberOfTestRuns : function(response)
    {
        $("#number-of-test-runs-LED-gauge").sevenSeg({
            digits:Math.abs(response.data.numberOfTestRuns).toString().length,
            value:response.data.numberOfTestRuns,
            colorOff: "#003200",
            colorOn: "Lime",
            slant: 0
        });
    },


    showMeanTimeToRepair : function(response)
    {
        $("#mean-time-to-repair-area").html(response.data.meanTimeToRepair);
    },


    showPeopleToBlame : function(response)
    {
        let numberOfPeopleToBlame = 0;
        let peopleToBlame = '';

        response.data.blameBoardData.forEach(blame => {
            numberOfPeopleToBlame++;
            peopleToBlame += blame.name + ' -- ' + blame.email + '<br>';
        });

        $("#number-of-people-to-blame-LED-gauge").sevenSeg({
            digits:Math.abs(numberOfPeopleToBlame).toString().length,
            value:numberOfPeopleToBlame,
            colorOff: "#003200",
            colorOn: "Lime",
            slant: 0
        });

        if(peopleToBlame.length === 0)
        {
            peopleToBlame = "WOW! No one to blame. What is going on? No one is blameless";
        }

        $("#people-to-blame-area").html(peopleToBlame);
        let mostFailedRepos = '';

        response.data.mostFailedRepos.forEach(repo => {
            mostFailedRepos += '<a href="/repoDetails?repo=' + repo + '" target="_blank" class="anchor-text pe-auto text-primary fw-medium text-decoration-underline">' + repo + '</a><br>';
        });

        if(mostFailedRepos.length === 0)
        {
            mostFailedRepos = "WOW! No one to blame. What is going on? No one is blameless";
        }

        $("#most-failed-repos-area").html(mostFailedRepos);
    },


    showFixedPipelineRunsByBlamedPerson : function(response)
    {
        let listHTML = '';

        response.data.data.forEach(pairOfBlamedPersonAndFixedRuns => {
            listHTML += `
                <li>
                    <a href="javascript:void(0);">
                        <div class="d-flex align-items-center justify-content-between mb-1 fs-13">
                            <div>${pairOfBlamedPersonAndFixedRuns.first}</div>
                            <div>${pairOfBlamedPersonAndFixedRuns.second}%</div>
                        </div>
                        <div>
                            <div class="progress rounded-0 progress-sm border border-primary border-opacity-10 custom-progress-padding" role="progressbar" aria-label="Basic example" aria-valuenow="${pairOfBlamedPersonAndFixedRuns.second}" aria-valuemin="0" aria-valuemax="100">
                                <div class="progress-bar" style="width: ${pairOfBlamedPersonAndFixedRuns.second}%"><div class="progress-before"></div></div>
                            </div>
                        </div>
                    </a>    
                </li>
            `;
        });

        $("#fixed-pipeline-runs-by-blamed-person-area").html(listHTML);
    },


    showNumberOfFixedAndTotalFailedTestRuns : function(response)
    {
        $('#number-of-done-tests-gauge').each(function (index, item) {
            let params = {
                initialValue: response.data.numberOfFixedTestRuns,
                higherValue: response.data.numberOfFailedTestRuns,
                title: 'FIXED Over All Failed Test Runs',
                meterTicks: orionCommon.buildArrayOfRangeOfIntegers(10, 0, response.data.numberOfFailedTestRuns),
                subtitle: response.data.numberOfFixedTestRuns + ' Fixed'
            };
            let gauge = new GaugeChart(item, params);
            gauge.init();
        });
    },


    buildPipelineRunsByCommandBarChart : function(response)
    {
        let seriesLabels = [];
        let series = [];

        response.data.data.forEach(pairOfCommandAndNumberOfRuns => {
            seriesLabels.push(pairOfCommandAndNumberOfRuns.first);
            series.push(pairOfCommandAndNumberOfRuns.second);
        });

        orionCommon.buildApexCharBarChartWithDataLabels('Pipeline Runs by Command', seriesLabels, series, "#pipeline-runs-by-pipeline-command-bar-chart");
    },


    buildPipelineRunFailuresByCommandBarChart : function(response)
    {
        let seriesLabels = [];
        let series = [];

        response.data.data.forEach(pairOfCommandAndNumberOfRuns => {
            seriesLabels.push(pairOfCommandAndNumberOfRuns.first);
            series.push(pairOfCommandAndNumberOfRuns.second);
        });

        orionCommon.buildApexCharBarChartWithDataLabels('Pipeline Run Failures by Command', seriesLabels, series, "#pipeline-run-failures-by-pipeline-command-bar-chart");
    },


    buildAveragePipelineRunDurationByCommandColumnChart : function(response)
    {
        let seriesLabels = [];
        let series = [];

        response.data.data.forEach(pairOfCommandAndDuration => {
            seriesLabels.push(pairOfCommandAndDuration.first);
            series.push(pairOfCommandAndDuration.second);
        });

        orionCommon.buildApexCharColumnChart(seriesLabels, series, "#average-pipeline-run-duration-by-pipeline-command-column-chart");
    },


    buildPipelineFailureRatesPerDayOfRunsLineDatetimeChart : function(response)
    {
        let series = [];
        let maximumFailureRate = 0;

        response.data.data.forEach(pairOfDateAndFailureRate => {
            series.push([new Date(pairOfDateAndFailureRate.first).getTime(), pairOfDateAndFailureRate.second]);

            if(pairOfDateAndFailureRate.second > maximumFailureRate)
            {
                maximumFailureRate = pairOfDateAndFailureRate.second;
            }
        });

        series.sort((a, b) => a[0] - b[0]);
        orionCommon.buildApexChartLineChart('Percentage of Fixed Runs', series, 'datetime', maximumFailureRate, "%", "#pipeline-failure-rates-per-day-of-runs-line-datetime-chart");
    },


    buildPipelineRunsLineDatetimeChart : function(response)
    {
        let series = [];
        let maximumNumberOfRuns = 0;

        response.data.data.forEach(pairOfDateAndNumberOfRuns => {
            series.push([new Date(pairOfDateAndNumberOfRuns.first).getTime(), pairOfDateAndNumberOfRuns.second]);

            if(pairOfDateAndNumberOfRuns.second > maximumNumberOfRuns)
            {
                maximumNumberOfRuns = pairOfDateAndNumberOfRuns.second;
            }
        });

        series.sort((a, b) => a[0] - b[0]);
        orionCommon.buildApexChartLineChart('Number of Runs', series, 'datetime', maximumNumberOfRuns, "", "#pipeline-runs-line-datetime-chart");
    }
};