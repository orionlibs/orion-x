window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let runPipelineCommandButtonID;
let repositorySequenceID;
let sequenceRunID;
let repoTestRunHistoryTable;


$(document).ready(function()
{
    sequentialPipelineRunPage.getRepoSequenceID();
    sequentialPipelineRunPage.loadRepoSequenceDetailsComponent();
    orionCommon.makeGetAJAXCall('/api/repositories/sequences/' + repositorySequenceID + '/latest-pipeline-run-id', sequentialPipelineRunPage.loadLastRepoSequencePipelineRunID);
    orionCommon.makeGetAJAXCall('/api/repositories/sequences/' + repositorySequenceID + '/details', sequentialPipelineRunPage.loadRepoSequenceDetails);
    orionCommon.makeGetAJAXCall('/api/repositories/sequences/' + repositorySequenceID + '/pipelines/latest/commands/tests/executions', sequentialPipelineRunPage.loadRepoTestRunHistoryTable);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let sequentialPipelineRunPage =
    {
        getRepoSequenceID : function()
        {
            const params = new URLSearchParams(window.location.search);
            let repo = params.get('repoSequence');

            if(repo.endsWith("#"))
            {
                repo = repo.substring(0, repo.length - 1);
            }

            repositorySequenceID = repo;

            if (!repo) {
                alert('No repository specified');
                return null;
            } else {
                return repo;
            }
        },


        loadLastRepoSequencePipelineRunID : function(response)
        {
            sequenceRunID = response.data.latestPipelineRunID;
        },


        loadRepoSequenceDetailsComponent : async function () {
            const componentPromises = [
                orionCommon.loadComponent('imports/sequentialPipelineRun/repoSequenceDetailsArea.html', 'repo-sequence-details-area')
            ];

            await Promise.all(componentPromises);
        },


        loadRepoSequenceDetails : function(response)
        {
            $("#repo-sequence-name").html(response.data.name);
            let reposHTML = '';

            response.data.repositories.forEach(repo => {
                reposHTML += '<span style="display: flex; align-items: center; gap: 20px;">';
                reposHTML += '<a href="/repoDetails?repo=' + repo.repositoryName + '" target="_blank" class="pe-auto text-primary fw-medium text-decoration-underline">' + repo.repositoryName + '</a>';
                reposHTML += '<input id="input-pipeline-command-' + repo.repositoryName + '" class="pipeline-command" type="hidden" value="e2e-tests"/>';

                if(repo.pipelineCommands !== undefined
                    && repo.pipelineCommands !== null
                    && repo.pipelineCommands.length > 0)
                {
                    reposHTML += `
                        <input id="input-execution-environment-for-pipeline-command-mode-${repo.repositoryName}" class="environments" type="hidden" value="DEVELOPMENT"/>
                        <select id="input-pipeline-command-dropdown-${repo.repositoryName}" class="form-select" style="width: 10rem">
                    `;

                    repo.pipelineCommands.forEach(command => {
                        reposHTML += '<option value="' + command + '">' + command + '</option>';
                    });

                    reposHTML += '</select>';

                    reposHTML += `
                        <select id="input-execution-environment-mode-for-pipeline-command-dropdown-${repo.repositoryName}" class="form-select" style="width: 10rem">
                            <option value="DEVELOPMENT" selected>development</option>
                            <option value="STAGING">staging</option>
                        </select>
                        <span id="staging-branch-message-for-pipeline-command-${repo.repositoryName}" class="hidden">Staging only has master</span>
                        <input id="input-branch-for-pipeline-command-${repo.repositoryName}" class="branches" type="text" value="master"/>
                    `;
                }

                reposHTML += '</span>';
                reposHTML += '<br><br>';
                $("#input-execution-environment-for-pipeline-command-mode-" + repo.repositoryName).val("DEVELOPMENT");


                $("#input-execution-environment-mode-for-pipeline-command-dropdown-" + repo.repositoryName).on("change", function () {
                    let selectedValue = $(this).val();

                    if(selectedValue === "STAGING")
                    {
                        $("#staging-branch-message-for-pipeline-command-" + repo.repositoryName).removeClass("hidden");
                        $("#input-branch-for-pipeline-command-" + repo.repositoryName).attr("disabled", true);
                        $("#input-branch-for-pipeline-command-" + repo.repositoryName).val("master");
                    }
                    else
                    {
                        $("#staging-branch-message-for-pipeline-command-" + repo.repositoryName).addClass("hidden");
                        $("#input-branch-for-pipeline-command-" + repo.repositoryName).attr("disabled", false);
                    }

                    $("#input-execution-environment-for-pipeline-command-mode-" + repo.repositoryName).val(selectedValue);
                });


                $("#input-pipeline-command-dropdown-" + repo.repositoryName).on("change", function () {
                    let selectedValue = $(this).val();
                    $("#input-pipeline-command-" + repo.repositoryName).val(selectedValue);
                });
            });

            reposHTML += '<a id="run-pipeline-sequence-button" href="#" class="btn btn-primary btn-sm btn-rounded px-3 fw-600 rounded">Run Sequence</a>';
            reposHTML += `<br><br><div class="input-group mb-3 form-check form-switch">
                            <input class="form-check-input" type="checkbox" role="switch" id="parallelism-mode-switch">
                                &nbsp;&nbsp;<label class="form-check-label" for="parallelism-mode-switch">Sequential Run / Parallel Run</label>
                        </div>`;

            runPipelineCommandButtonID = '#run-pipeline-sequence-button';
            $("#repos-details-area").html(reposHTML);


            $('body').on('click', '#run-pipeline-sequence-button', function(e)
            {
                e.preventDefault();
                orionCommon.enablePreloaderForElement(runPipelineCommandButtonID, "Wait...");
                let pipelineCommands = [];

                $('.pipeline-command').each(function(index, element) {

                    pipelineCommands.push($(element).val());
                });

                let environments = [];

                $('.environments').each(function(index, element) {

                    environments.push($(element).val());
                });

                let branches = [];

                $('.branches').each(function(index, element) {

                    branches.push($(element).val());
                });

                let runInParallel = $('#parallelism-mode-switch').is(':checked');
                let dataToSend = {};
                dataToSend["pipelineCommands"] = pipelineCommands;
                dataToSend["environments"] = environments;
                dataToSend["branches"] = branches;
                dataToSend["runInParallel"] = runInParallel;
                orionCommon.makePostAJAXCall('/api/repositories/sequences/' + repositorySequenceID + '/pipelines/commands/executions', dataToSend, sequentialPipelineRunPage.processPipelineCommandExecution);
            });


            $('body').on('click', '#refresh-test-run-history-table-button', function(e)
            {
                if(repoTestRunHistoryTable !== null && repoTestRunHistoryTable)
                {
                    repoTestRunHistoryTable.clear().destroy();
                }

                orionCommon.makeGetAJAXCall('/api/repositories/sequences/' + repositorySequenceID + '/pipelines/' + sequenceRunID + '/commands/tests/executions', sequentialPipelineRunPage.loadRepoTestRunHistoryTable);
            });
        },


        processPipelineCommandExecution : function(response)
        {
            sequenceRunID = response.data.sequenceRunID;

            if(sequenceRunID !== "0")
            {
                orionCommon.showNotification('Started!', 'Pipeline run started. Please refresh the run table', 6000);
            }
            else
            {
                orionCommon.showNotification('Error!', 'Problem starting the pipeline run', 3000);
            }

            orionCommon.disablePreloaderOfElement(runPipelineCommandButtonID);
        },


        loadRepoTestRunHistoryTable : function(response)
        {
            response.data.runs.forEach(testRun => {
                let tableBodyHTML = '<tr>';
                tableBodyHTML += '<td class="align-middle table-text">' + testRun.repositoryName + '</td>';
                tableBodyHTML += '<td class="align-middle table-text">' + testRun.status + ' (' + testRun.pipelineCommand + ' -- ' + testRun.environment + ' -- ' + testRun.branch + ')</td>';

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

                tableBodyHTML += '<td class="align-middle">';
                tableBodyHTML += '<a href="' + testRun.pipelineRunUrl + '" target="_blank">Open Pipeline</a>';
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
                tableBodyHTML += '<td class="align-middle table-text">' + testRun.startedAt + '</td>';
                tableBodyHTML += '</tr>';
                $("#repo-test-run-history-table-body").append(tableBodyHTML);


                $('body').on('click', '#open-logs-button-' + testRun.pipelineBuildNumber, function(e)
                {
                    orionCommon.makeGetAJAXCall('/api/repositories/' + testRun.repositoryName + '/tests/logs/pipelines/' + testRun.pipelineBuildNumber, sequentialPipelineRunPage.loadRepoTestLogs);
                });


                $('body').on('click', '#stop-test-run-button-' + testRun.pipelineBuildNumber, function(e)
                {
                    orionCommon.makeDeleteAJAXCall('/api/repositories/' + testRun.repositoryName + '/pipelines/' + testRun.pipelineBuildNumber, sequentialPipelineRunPage.stopTestRunForPipelineResult, sequentialPipelineRunPage.stopTestRunForPipelineResult);
                });
            });

            sequentialPipelineRunPage.renderRepoTestRunHistoryTableData();
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


        stopTestRunForPipelineResult : function(response)
        {
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