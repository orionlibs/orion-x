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
    flatpickr("#targetDate", {
        enableTime: true,
        dateFormat: "Y-m-d H:i",
    });

    /* filepond */
    FilePond.registerPlugin(
        FilePondPluginImagePreview,
        FilePondPluginImageExifOrientation,
        FilePondPluginFileValidateSize,
        FilePondPluginFileEncode,
        FilePondPluginImageEdit,
        FilePondPluginFileValidateType,
        FilePondPluginImageCrop,
        FilePondPluginImageResize,
        FilePondPluginImageTransform
    );

    orionCommon.makeGetAJAXCall('/api/kanban?numberOfDays=30&status=IN_PROGRESS', kanbanBoardPage.loadKanbanInProgressTickets);
    orionCommon.makeGetAJAXCall('/api/kanban?numberOfDays=30&status=TODO', kanbanBoardPage.loadKanbanToDoTickets);
    orionCommon.makeGetAJAXCall('/api/kanban?numberOfDays=30&status=COMPLETED', kanbanBoardPage.loadKanbanCompletedTickets);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let kanbanBoardPage =
{
    loadKanbanInProgressTickets : function(response)
    {
        let myElement = document.getElementById('inprogress-tasks');
        new SimpleBar(myElement, { autoHide: true });
        $("#number-of-current-test-runs").html(response.data.numberOfTickets);

        response.data.kanbanBoardData.forEach(ticket => {
            const ticketHTML = `
                <div class="card custom-card">
                    <div class="top-left"></div>
                    <div class="top-right"></div>
                    <div class="bottom-left"></div>
                    <div class="bottom-right"></div>
            
            
                    <div class="card-body p-0">
                        <div class="p-3 kanban-board-head">
                            <div class="d-flex align-items-center justify-content-between gap-2">
                                <div>
                                    <h6 class="fw-medium mb-0"><a href="/repoDetails?repo=${ticket.repositoryName}" class="pe-auto text-primary fw-medium text-decoration-underline">${ticket.repositoryName}</a></h6>
                                </div>
                            </div>
                            <div class="task-badges"><span class="badge bg-primary-transparent">${ticket.name} -- ${ticket.email}</span><span class="ms-1 badge bg-danger-transparent" style="text-decoration: underline;"><a href="${ticket.pipelineURL}">Pipeline URL</a></span></div>
                            <div class="task-badges">
                                <span class="ms-1 badge bg-warning-transparent" style="text-decoration: underline;">
                                    <a href="${ticket.pullRequestURL}">PR URL</a>
                                </span>
                                <span class="ms-1 badge bg-warning-transparent" style="text-decoration: underline;">
                                    <a href="${ticket.ticketURL}">Ticket URL</a>
                                </span>
                            </div>
                            <div class="kanban-content mt-2">
                                <div class="d-flex justify-content-between gap-2">
                                    <div class="fs-11 mb-1"><i class="ri-calendar-line me-1 align-middle d-inline-block op-7"></i>Triggerred: ${ticket.createdDateTime}</div>
                                    <div class="fs-11"><i class="ri-progress-5-line me-1 align-middle d-inline-block op-7"></i>Triggerer: <span class="text-warning">${ticket.userTriggerer} </span></div>
                                </div>
                                <div class="kanban-task-description op-8 mb-1">${ticket.pipelineCommandExecuted} -- ${ticket.environment} -- ${ticket.branch}</div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            $("#in-progress-area").append(ticketHTML);
        });
    },


    loadKanbanToDoTickets : function(response)
    {
        let myElement = document.getElementById('todo-tasks');
        new SimpleBar(myElement, { autoHide: true });
        $("#number-of-test-run-failures").html(response.data.numberOfTickets);

        response.data.kanbanBoardData.forEach(ticket => {
            let logsButtonHTML = '';

            if(ticket.logsExist)
            {
                logsButtonHTML = '<a id="open-logs-button-' + ticket.repositoryName + '-' + ticket.pipelineBuildNumber + '" href="javascript:void(0);" class="me-2 text-secondary" style="float: right" data-bs-toggle="modal" data-bs-target="#testLogsModal"><span class="me-1"><i class="ri-attachment-2 fw-normal"></i></span><span class="fw-medium fs-12">Logs</span></a>';
            }

            const ticketHTML = `
                <div class="card custom-card">
                    <div class="top-left"></div>
                    <div class="top-right"></div>
                    <div class="bottom-left"></div>
                    <div class="bottom-right"></div>
            
            
                    <div class="card-body p-0">
                        <div class="p-3 kanban-board-head">
                            <div class="d-flex align-items-center justify-content-between gap-2">
                                <div>
                                    <h6 class="fw-medium mb-0"><a href="/repoDetails?repo=${ticket.repositoryName}" class="pe-auto text-primary fw-medium text-decoration-underline">${ticket.repositoryName}</a></h6>
                                </div>
                            </div>
                            <div class="task-badges"><span class="badge bg-primary-transparent">${ticket.name} -- ${ticket.email}</span><span class="ms-1 badge bg-danger-transparent" style="text-decoration: underline;"><a href="${ticket.pipelineURL}">Pipeline URL</a></span></div>
                            <div class="task-badges">
                                <span class="ms-1 badge bg-warning-transparent" style="text-decoration: underline;">
                                    <a href="${ticket.pullRequestURL}">PR URL</a>
                                </span>
                                <span class="ms-1 badge bg-warning-transparent" style="text-decoration: underline;">
                                    <a href="${ticket.ticketURL}">Ticket URL</a>
                                </span>
                            </div>
                            <div class="kanban-content mt-2">
                                <div class="d-flex justify-content-between gap-2">
                                    <div class="fs-11 mb-1"><i class="ri-calendar-line me-1 align-middle d-inline-block op-7"></i>Failed: ${ticket.failureDateTime}</div>
                                    <div class="fs-11"><i class="ri-progress-5-line me-1 align-middle d-inline-block op-7"></i>Triggerer: <span class="text-warning">${ticket.userTriggerer} </span></div>
                                </div>
                                <div class="kanban-task-description op-8 mb-1">${ticket.pipelineCommandExecuted} -- ${ticket.environment} -- ${ticket.branch}${logsButtonHTML}</div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            $("#todo-area").append(ticketHTML);

            $('body').on('click', '#open-logs-button-' + ticket.repositoryName + '-' + ticket.pipelineBuildNumber, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + ticket.repositoryName + '/tests/logs/pipelines/' + ticket.pipelineBuildNumber, kanbanBoardPage.loadRepoTestLogs);
            });
        });
    },


    loadKanbanCompletedTickets : function(response)
    {
        let myElement = document.getElementById('completed-tasks');
        new SimpleBar(myElement, { autoHide: true });
        $("#number-of-fixed-test-runs").html(response.data.numberOfTickets);

        response.data.kanbanBoardData.forEach(ticket => {
            const ticketHTML = `
                <div class="card custom-card">
                    <div class="top-left"></div>
                    <div class="top-right"></div>
                    <div class="bottom-left"></div>
                    <div class="bottom-right"></div>
            
            
                    <div class="card-body p-0">
                        <div class="p-3 kanban-board-head">
                            <div class="d-flex align-items-center justify-content-between gap-2">
                                <div>
                                    <h6 class="fw-medium mb-0"><a href="/repoDetails?repo=${ticket.repositoryName}" class="pe-auto text-primary fw-medium text-decoration-underline">${ticket.repositoryName}</a></h6>
                                </div>
                            </div>
                            <div class="task-badges">
                                <span class="badge bg-primary-transparent">${ticket.name}</span>
                                <span class="ms-1 badge bg-danger-transparent" style="text-decoration: underline;">
                                    <a href="${ticket.pipelineURL}">Pipeline URL</a>
                                </span>
                                <span class="ms-1 badge bg-success-transparent">Fixed After ${ticket.fixedAfter}</span>
                            </div>
                            <div class="kanban-content mt-2">
                                <div class="d-flex justify-content-between gap-2">
                                    <div class="fs-11 mb-1"><i class="ri-calendar-line me-1 align-middle d-inline-block op-7"></i>Fixed: ${ticket.testRunFixDateTime}</div>
                                    <div class="fs-11"><i class="ri-progress-5-line me-1 align-middle d-inline-block op-7"></i>Triggerer: <span class="text-warning">${ticket.userTriggerer} </span></div>
                                </div>
                                <div class="kanban-task-description op-8 mb-1">${ticket.pipelineCommandExecuted} -- ${ticket.environment} -- ${ticket.branch}</div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            $("#completed-area").append(ticketHTML);
        });
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
    }
};