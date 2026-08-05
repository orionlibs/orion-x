window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let runPipelineCommandButtonID;
let repositoryName;
let oldPipelineBuildNumber;
let pipelineBuildNumberForCommand;
let repoTestRunHistoryTable;


$(document).ready(function()
{
    repoDetailsPage.getRepoName();

    $('body').on('click', '#refresh-repo-pipeline-status-button', function(e)
    {
        orionCommon.makeGetAJAXCall('/api/repositories/' + repositoryName + '/pipeline-status', repoDetailsPage.refreshRepoPipelineStatus);
    });

    repoDetailsPage.loadRepoDetailsComponent();
    orionCommon.makeGetAJAXCall('/api/repositories/' + repositoryName + '/details', repoDetailsPage.loadRepoDetails);
    orionCommon.makeGetAJAXCall('/api/repositories/' + repositoryName + '/pipeline-status', repoDetailsPage.refreshRepoPipelineStatus);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let repoDetailsPage =
{
    getRepoName : function()
    {
        const params = new URLSearchParams(window.location.search);
        let repo = params.get('repo');

        if(repo.endsWith("#"))
        {
            repo = repo.substring(0, repo.length - 1);
        }

        repositoryName = repo;

        if (!repo) {
            alert('No repository specified');
            return null;
        } else {
            return repo;
        }
    },


    loadRepoDetailsComponent : async function () {
        const componentPromises = [
            orionCommon.loadComponent('imports/repoDetails/repoDetailsArea.html', 'repo-details-area')
        ];

        await Promise.all(componentPromises);
    },


    loadRepoDetails : function(response)
    {
        $("#repo-name").html(response.data.repositoryData.fullName);
        $("#repo-owner").html(' (Owner: ' + response.data.repositoryData.owner + ')');

        if(response.data.repositoryData.pipelineCommands !== undefined
            && response.data.repositoryData.pipelineCommands !== null
            && response.data.repositoryData.pipelineCommands.length > 0)
        {
            let pipelineCommandsHTML = '<a id="run-pipeline-command-button" href="#" class="btn btn-primary btn-sm btn-rounded px-3 fw-600 rounded">Run Pipeline Command</a>';
            pipelineCommandsHTML += '<input id="input-pipeline-command" type="hidden" value="e2e-tests"/>';
            pipelineCommandsHTML += '<input id="input-execution-environment-for-pipeline-command-mode" type="hidden" value="DEVELOPMENT"/>';
            pipelineCommandsHTML += '<select id="input-pipeline-command-dropdown" class="form-select" style="width: 10rem">';

            response.data.repositoryData.pipelineCommands.forEach(command => {
                pipelineCommandsHTML += '<option value="' + command + '">' + command + '</option>';
            });

            pipelineCommandsHTML += '</select>';
            pipelineCommandsHTML += '<select id="input-execution-environment-mode-for-pipeline-command-dropdown" class="form-select" style="width: 10rem">';
            pipelineCommandsHTML += '<option value="DEVELOPMENT" selected>development</option>';
            pipelineCommandsHTML += '<option value="STAGING">staging</option>';
            pipelineCommandsHTML += '</select>';
            pipelineCommandsHTML += '<span id="staging-branch-message-for-pipeline-command" class="hidden">Staging only has master</span>';
            pipelineCommandsHTML += '<input id="input-branch-for-pipeline-command" type="text" value="master"/>';
            pipelineCommandsHTML += '<span>Schedule Run&nbsp;&nbsp;</span>';
            pipelineCommandsHTML += '<div class="form-group">';
            pipelineCommandsHTML += '<div class="input-group">';
            pipelineCommandsHTML += '<input type="text" class="form-control" id="datetime-picker" placeholder="Choose date with time">';
            pipelineCommandsHTML += '</div>';
            pipelineCommandsHTML += '</div><br><br>';
            pipelineCommandsHTML += '<a id="repo-pipeline-command-run-pipeline-url" href="#" target="_blank" class="btn btn-success btn-sm btn-rounded px-3 fw-600 rounded hidden">Open Pipeline</a>';
            pipelineCommandsHTML += '<a id="stop-pipeline-command-run-button" href="#" class="btn btn-danger btn-sm btn-rounded px-3 fw-600 rounded hidden">Stop Run</a>';
            $("#repo-run-pipeline-command-button-area").html(pipelineCommandsHTML);
            $("#input-execution-environment-for-pipeline-command-mode").val("DEVELOPMENT");

            flatpickr("#datetime-picker", {
                enableTime: true,
                dateFormat: "Y-m-d H:i",
            });

            $("#input-execution-environment-mode-for-pipeline-command-dropdown").on("change", function () {
                let selectedValue = $(this).val();

                if(selectedValue === "STAGING")
                {
                    $("#staging-branch-message-for-pipeline-command").removeClass("hidden");
                    $("#input-branch-for-pipeline-command").attr("disabled", true);
                    $("#input-branch-for-pipeline-command").val("master");
                }
                else
                {
                    $("#staging-branch-message-for-pipeline-command").addClass("hidden");
                    $("#input-branch-for-pipeline-command").attr("disabled", false);
                }

                $("#input-execution-environment-for-pipeline-command-mode").val(selectedValue);
            });

            $("#input-pipeline-command-dropdown").on("change", function () {
                let selectedValue = $(this).val();
                $("#input-pipeline-command").val(selectedValue);
            });

            runPipelineCommandButtonID = '#run-pipeline-command-button';


            $('body').on('click', '#run-pipeline-command-button', function(e)
            {
                e.preventDefault();
                orionCommon.enablePreloaderForElement(runPipelineCommandButtonID, "Wait...");
                let dataToSend = {};
                dataToSend["pipelineCommand"] = $("#input-pipeline-command").val();
                dataToSend["scheduledDateTime"] = $('#datetime-picker').val();
                orionCommon.makePostAJAXCall('/api/repositories/' + repositoryName + '/pipelines/commands/executions?environment=' + $("#input-execution-environment-for-pipeline-command-mode").val() + '&branch=' + $("#input-branch-for-pipeline-command").val(), dataToSend, repoDetailsPage.processPipelineCommandExecution);
            });


            $('body').on('click', '#stop-pipeline-command-run-button', function(e)
            {
                repoDetailsPage.stopPipelineCommandRun();
            });
        }

        orionCommon.makeGetAJAXCall('/api/repositories/' + response.data.repositoryData.name + '/pipelines/commands/tests/executions', repoDetailsPage.loadRepoTestRunHistoryTable);


        $('body').on('click', '#refresh-test-run-history-table-button', function(e)
        {
            repoTestRunHistoryTable.clear().destroy();
            orionCommon.makeGetAJAXCall('/api/repositories/' + response.data.repositoryData.name + '/pipelines/commands/tests/executions', repoDetailsPage.loadRepoTestRunHistoryTable);
        });
    },


    loadRepoTestRunHistoryTable : function(response)
    {
        response.data.testRunHistoryData.forEach(testRun => {
            let tableBodyHTML = '<tr>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.status + ' (' + testRun.pipelineCommandExecuted + ' -- ' + testRun.environment + ' -- ' + testRun.branch + ')</td>';

            if (testRun.status?.toLowerCase() != "completed"
                && testRun.status?.toLowerCase() != "successful"
                && testRun.status?.toLowerCase() != "stopped"
                && testRun.status?.toLowerCase() != "failed") {
                tableBodyHTML += '<td class="align-middle">';
                tableBodyHTML += '<a id="stop-test-run-button-' + testRun.pipelineBuildNumber + '" href="#" class="btn btn-danger btn-sm btn-rounded px-3 fw-600 rounded">Stop</a>';
                tableBodyHTML += '</td>';
            }
            else
            {
                tableBodyHTML += '<td class="align-middle"></td>';
            }

            tableBodyHTML += '<td class="align-middle table-text">' + testRun.userTriggerer + '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.committer + '</td>';
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
            tableBodyHTML += '<td class="align-middle">';

            if(testRun.logsExist)
            {
                tableBodyHTML += '<a id="open-logs-button-' + testRun.pipelineBuildNumber + '" href="#" data-bs-toggle="modal" data-bs-target="#testLogsModal">Open Logs</a>';
            }
            else
            {
                tableBodyHTML += '<span class="badge border border-danger text-danger px-2 pt-5px pb-5px rounded fs-12px d-inline-flex align-items-center"><i class="fa fa-circle fs-9px fa-fw me-5px"></i> No Logs</span>';
            }

            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.buildDurationInSeconds + 's</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.createdAt + '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + testRun.updatedAt + '</td>';
            tableBodyHTML += '</tr>';
            $("#repo-test-run-history-table-body").append(tableBodyHTML);


            $('body').on('click', '#open-logs-button-' + testRun.pipelineBuildNumber, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + repositoryName + '/tests/logs/pipelines/' + testRun.pipelineBuildNumber, repoDetailsPage.loadRepoTestLogs);
            });


            $('body').on('click', '#stop-test-run-button-' + testRun.pipelineBuildNumber, function(e)
            {
                repoDetailsPage.stopTestRunForPipeline(testRun.pipelineBuildNumber);
            });
        });

        repoDetailsPage.renderRepoTestRunHistoryTableData();
    },


    refreshRepoPipelineStatus : function(response)
    {
        $("#repo-pipeline-status").html(response.data.pipelineStatus);
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


    stopTestRunForPipeline : function(pipelineBuildNumber)
    {
        oldPipelineBuildNumber = pipelineBuildNumber;
        orionCommon.enablePreloaderForElement("#stop-test-run-button-" + pipelineBuildNumber, "Wait...");
        orionCommon.makeDeleteAJAXCall('/api/repositories/' + repositoryName + '/pipelines/' + pipelineBuildNumber, repoDetailsPage.stopTestRunForPipelineResult, repoDetailsPage.stopTestRunForPipelineResult);
    },


    stopTestRunForPipelineResult : function(response)
    {
        orionCommon.disablePreloaderOfElement("#stop-test-run-button-" + oldPipelineBuildNumber);

        if(response.data.stopped)
        {
            orionCommon.showNotification('Stopped!', 'Test run stopped', 3000);
        }
        else
        {
            orionCommon.showNotification('Error!', 'Error stopping test run. Maybe it already stopped. Please wait for 1 minute for the backend to refresh ststus', 6000);
        }
    },


    processPipelineCommandExecution : function(response)
    {
        pipelineBuildNumberForCommand = response.data.pipelineBuildNumber;

        if(pipelineBuildNumberForCommand === 0)
        {
            $("#repo-pipeline-command-run-pipeline-url").addClass("hidden");
            $("#stop-pipeline-command-run-button").addClass("hidden");
            orionCommon.showNotification('Started!', 'Pipeline run scheduled', 3000);
        }
        else if(pipelineBuildNumberForCommand > 0)
        {
            $("#repo-pipeline-command-run-pipeline-url").attr("href", response.data.testRunPipelineURL);
            $("#repo-pipeline-command-run-pipeline-url").html("Open Pipeline (" + response.data.branch + ")");
            $("#repo-pipeline-command-run-pipeline-url").removeClass("hidden");
            $("#stop-pipeline-command-run-button").removeClass("hidden");
        }

        orionCommon.disablePreloaderOfElement(runPipelineCommandButtonID);
    },


    stopPipelineCommandRun : function()
    {
        orionCommon.enablePreloaderForElement("#stop-pipeline-command-run-button", "Wait...");
        orionCommon.makeDeleteAJAXCall('/api/repositories/' + repositoryName + '/pipelines/' + pipelineBuildNumberForCommand, repoDetailsPage.stopPipelineCommandRunResult, repoDetailsPage.stopPipelineCommandRunResult);
    },


    stopPipelineCommandRunResult : function(response)
    {
        orionCommon.disablePreloaderOfElement("#stop-pipeline-command-run-button");

        if(response.data.stopped)
        {
            orionCommon.showNotification('Stopped!', 'Test run stopped', 3000);
        }
        else
        {
            orionCommon.showNotification('Error!', 'Error stopping test run. Maybe it already stopped. Please wait for 1 minute for the backend to refresh ststus', 6000);
        }
    },


    renderRepoTestRunHistoryTableData : function()
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

        repoTestRunHistoryTable = new DataTable('#repo-test-run-history-table', options);
        $(window).trigger('resize');
    }
};