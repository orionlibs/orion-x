window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let blameBoardTable;


$(document).ready(function()
{
    blameBoardPage.loadBlameBoardTableComponent();
    orionCommon.makeGetAJAXCall('/api/blames?numberOfDays=30', blameBoardPage.loadBlameBoardTable);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let blameBoardPage =
{
    loadBlameBoardTableComponent : async function () {
        const componentPromises = [
            orionCommon.loadComponent('imports/blameBoard/blameBoardTable.html', 'blame-board-table-area')
        ];

        await Promise.all(componentPromises);
    },


    loadBlameBoardTable : function(response)
    {
        response.data.blameBoardData.forEach(blame => {
            let tableBodyHTML = '<tr>';
            tableBodyHTML += '<td class="align-middle table-text">' + blame.name + '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + blame.email + '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + blame.fails + '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a id="open-failed-repos-button-' + blame.id + '" href="#" data-bs-toggle="modal" data-bs-target="#failedReposModal">Failed Repos</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle table-text">' + blame.failsLastWeek + '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + blame.lastFailPipelineURL + '" target="_blank">Open Pipeline</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';

            if(blame.logsExist)
            {
                tableBodyHTML += '<a id="open-logs-button-' + blame.lastFailPipelineBuildNumber + '" href="#" data-bs-toggle="modal" data-bs-target="#testLogsModal">Open Logs</a>';
            }
            else
            {
                tableBodyHTML += '<span class="badge border border-danger text-danger px-2 pt-5px pb-5px rounded fs-12px d-inline-flex align-items-center"><i class="fa fa-circle fs-9px fa-fw me-5px"></i> No Logs</span>';
            }

            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + blame.lastFailPullRequestURL + '" target="_blank">Open PR</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + blame.lastFailTicketURL + '" target="_blank">Open Ticket</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="/repoDetails?repo=' + blame.mostFailedRepo + '" target="_blank">' + blame.mostFailedRepo + '</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '</td>';
            $("#blame-board-table-body").append(tableBodyHTML);
            let failedReposHTML = '';

            blame.failedRepos.forEach(failedRepo => {
                failedReposHTML += '<div id="failed-repos-modal-body-' + blame.id + '" class="failed-repo hidden">';
                failedReposHTML += '<a href="/repoDetails?repo=' + failedRepo + '" target="_blank" class="anchor-text">' + failedRepo + '</a><br>';
                failedReposHTML += '</div>';
            });

            $("#failed-repos-area").append(failedReposHTML);


            $('body').on('click', '#open-failed-repos-button-' + blame.id, function(e)
            {
                $(".failed-repo").addClass("hidden");
                $("#failed-repos-modal-body-" + blame.id).removeClass("hidden");
            });


            $('body').on('click', '#open-logs-button-' + blame.lastFailPipelineBuildNumber, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + blame.lastFailRepositoryName + '/tests/logs/pipelines/' + blame.lastFailPipelineBuildNumber, blameBoardPage.loadRepoTestLogs);
            });
        });

        blameBoardPage.renderBlameBoardTableData();
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


    renderBlameBoardTableData : function()
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

        blameBoardTable = new DataTable('#blame-board-table', options);
        $(window).trigger('resize');
    }
};