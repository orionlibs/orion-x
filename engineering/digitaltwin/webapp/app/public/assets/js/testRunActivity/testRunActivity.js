window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let repositoryNameTestRunToStop;
let pipelineBuildNumberTestRunToStop;
let testRunActivityTable;


$(document).ready(function()
{
    testRunActivityPage.loadTestRunActivityTableComponent();
    orionCommon.makeGetAJAXCall('/api/repositories/pipelines/commands/tests/executions', testRunActivityPage.loadTestRunActivityTable);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let testRunActivityPage =
{
    loadTestRunActivityTableComponent : async function () {
        const componentPromises = [
            orionCommon.loadComponent('imports/testRunActivity/testRunActivityTable.html', 'test-run-activity-table-area')
        ];

        await Promise.all(componentPromises);
    },


    loadTestRunActivityTable : function(response)
    {
        response.data.testRunHistoryData.forEach(testRun => {
            let tableBodyHTML = '<tr>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a id="open-repo-details-button-' + testRun.repositoryName + '" href="#">' + testRun.repositoryName + '</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.status + ' (' + testRun.pipelineCommandExecuted + ' -- ' + testRun.environment + ' -- ' + testRun.branch + ')</td>';

            if (testRun.status?.toLowerCase() != "completed"
                && testRun.status?.toLowerCase() != "successful"
                && testRun.status?.toLowerCase() != "stopped"
                && testRun.status?.toLowerCase() != "failed") {
                tableBodyHTML += '<td class="align-middle">';
                tableBodyHTML += '<a id="stop-test-run-button-' + testRun.repositoryName + '---' + testRun.pipelineBuildNumber + '" href="#" class="btn btn-danger btn-sm btn-rounded px-3 fw-600 rounded">Stop</a>';
                tableBodyHTML += '</td>';
            }
            else
            {
                tableBodyHTML += '<td class="align-middle"></td>';
            }

            tableBodyHTML += '<td class="align-middle">';

            if(testRun.logsExist)
            {
                tableBodyHTML += '<a id="open-logs-button-' + testRun.repositoryName + '-' + testRun.pipelineBuildNumber + '" href="#" data-bs-toggle="modal" data-bs-target="#testLogsModal">Open Logs</a>';
            }
            else
            {
                tableBodyHTML += '<span class="badge border border-danger text-danger px-2 pt-5px pb-5px rounded fs-12px d-inline-flex align-items-center"><i class="fa fa-circle fs-9px fa-fw me-5px"></i> No Logs</span>';
            }

            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + testRun.pipelineUrl + '" target="_blank">Open Pipeline</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + testRun.pullRequestUrl + '" target="_blank">Open PR</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + testRun.ticketUrl + '" target="_blank">Open Ticket</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + testRun.commitUrl + '" target="_blank">Open Commit</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.userTriggerer + '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.committer + '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.buildDurationInSeconds + 's</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.createdAt + '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.updatedAt + '</td>';
            tableBodyHTML += '</tr>';
            $("#test-run-activity-table-body").append(tableBodyHTML);


            $('body').on('click', '#open-repo-details-button-' + testRun.repositoryName, function(e)
            {
                const targetUrl = `http://localhost:8081/repoDetails?repo=${testRun.repositoryName}`;
                window.location.replace(targetUrl);
            });


            $('body').on('click', '#open-logs-button-' + testRun.repositoryName + '-' + testRun.pipelineBuildNumber, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + testRun.repositoryName + '/tests/logs/pipelines/' + testRun.pipelineBuildNumber, testRunActivityPage.loadRepoTestLogs);
            });


            $('body').on('click', '#stop-test-run-button-' + testRun.repositoryName + '---' + testRun.pipelineBuildNumber, function(e)
            {
                repositoryNameTestRunToStop = testRun.repositoryName;
                pipelineBuildNumberTestRunToStop = testRun.pipelineBuildNumber;
                testRunActivityPage.stopTestRunForPipeline();
            });
        });

        testRunActivityPage.renderTestRunActivityTableData();
    },


    stopTestRunForPipeline : function()
    {

        orionCommon.enablePreloaderForElement("#stop-test-run-button-" + repositoryNameTestRunToStop + '---' + pipelineBuildNumberTestRunToStop, "Wait...");
        orionCommon.makeDeleteAJAXCall('/api/repositories/' + repositoryNameTestRunToStop + '/pipelines/' + pipelineBuildNumberTestRunToStop, testRunActivityPage.stopTestRunForPipelineResult, testRunActivityPage.stopTestRunForPipelineResult);
    },


    stopTestRunForPipelineResult : function(response)
    {
        orionCommon.disablePreloaderOfElement("#stop-test-run-button-" + repositoryNameTestRunToStop + '---' + pipelineBuildNumberTestRunToStop);

        if(response.data.stopped)
        {
            orionCommon.showNotification('Stopped!', 'Test run stopped', 3000);
        }
        else
        {
            orionCommon.showNotification('Error!', 'Error stopping test run. Maybe it already stopped. Please wait for 1 minute for the backend to refresh ststus', 6000);
        }
    },


    loadRepoTestLogs : function(response)
    {
        let bodyHTML = '';
        let stepsLogsButtonsAreaHTML = '';

        response.data.logs.forEach(logData => {
            stepsLogsButtonsAreaHTML += '<button type="button" id="show-test-step-logs-button-' + logData.stepID + '" class="btn btn-secondary rounded-pill btn-wave">' + logData.pipelineStepName + '</button>';
            stepsLogsButtonsAreaHTML += '&nbsp;&nbsp;&nbsp;&nbsp;';
            bodyHTML += '<div id="test-step-logs-' + logData.stepID + '" class="hidden test-step-logs">' + logData.logs + '</div>';


            $('body').on('click', '#show-test-step-logs-button-' + logData.stepID, function(e)
            {
                $('.test-step-logs').each(function()
                {
                    const testStepLogAreaId = $(this).attr('id');

                    if (testStepLogAreaId === 'test-step-logs-' + logData.stepID)
                    {
                        $(this).removeClass("hidden");
                    }
                    else
                    {
                        $(this).addClass("hidden");
                    }
                });
            });
        });

        $("#pipeline-steps-logs-buttons-area").html(stepsLogsButtonsAreaHTML);
        $("#test-logs-modal-body").html(bodyHTML);
    },


    renderTestRunActivityTableData : function()
    {
        let options = {
            layout: {
                topStart: ['pageLength', 'search'],
                topEnd: 'buttons',
                bottomStart: 'info',
                bottomEnd: 'paging'
            },
            order: [],
            search: {
                return: true
            },
            buttons: [
                'copy', 'csv', 'excel', 'print'
            ],
            paging: true,
            //pageLength: 10,
            responsive: false,
            scrollX: true,
            scrollY: "auto",
            lengthMenu: [[10, 50, 100, -1], [10, 50, 100, "All"]]
        };

        testRunActivityTable = new DataTable('#test-run-activity-table', options);
        $(window).trigger('resize');
    }
};