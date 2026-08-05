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
    orionCommon.makeGetAJAXCall('/api/repositories/count', homePage.showNumberOfRepos);
    orionCommon.makeGetAJAXCall('/api/repositories/pipelines/commands/tests/executions/count', homePage.showNumberOfTestRuns);
    orionCommon.makeGetAJAXCall('/api/blames/summary?numberOfDays=30', homePage.showPeopleToBlame);
    orionCommon.makeGetAJAXCall('/api/analytics/pipelines/runs/mean-time-to-repair', homePage.showMeanTimeToRepair);
    orionCommon.makeGetAJAXCall('/api/repositories/pipelines/commands/tests/executions?numberOfRecentPipelineRuns=5', homePage.showRecentPipelineRunActivity);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let homePage =
{
    showNumberOfRepos : function(response)
    {
        $("#number-of-repos-LED-gauge").sevenSeg({
            digits:Math.abs(response.data.numberOfRepositories).toString().length,
            value:response.data.numberOfRepositories,
            colorOff: "#003200",
            colorOn: "Lime",
            slant: 0
        });
    },


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


    showMeanTimeToRepair : function(response)
    {
        $("#mean-time-to-repair-area").html(response.data.meanTimeToRepair);
    },


    showRecentPipelineRunActivity : function(response)
    {
        let listHTML = '';

        response.data.testRunHistoryData.forEach(recentAct => {
            let description = recentAct.status + ' (' + recentAct.pipelineCommandExecuted + ' -- ' + recentAct.environment + ' -- ' + recentAct.branch + ")";

            listHTML += `
                <li>
                    <div>
                        <h6 class="mb-1 fs-14">${recentAct.repositoryName}<span class="fs-11 float-end">${recentAct.createdAt}</span></h6>
                        <span class="d-block fs-13 fw-normal">${description} <a class="badge bg-secondary-transparent" href="${recentAct.pipelineUrl}">Open Pipeline</a></span>
                    </div>
                </li>
            `;
        });

        $("#recent-pipeline-run-activity-area").html(listHTML);
    }
};