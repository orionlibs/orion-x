window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let recentDeploymentsTable;


$(document).ready(function()
{
    recentDeploymentsPage.loadRecentDeploymentsTableComponent();


    $("#input-execution-environment-mode-dropdown").on("change", function () {
        let selectedValue = $(this).val();
        $("#input-execution-environment-mode").val(selectedValue);
    });


    $('body').on('click', '#retrieve-recent-deployments-button', function(e)
    {
        e.preventDefault();

        if(recentDeploymentsTable)
        {
            recentDeploymentsTable.clear().destroy();
        }

        orionCommon.enablePreloaderForElement('#retrieve-recent-deployments-button', "Wait...");
        orionCommon.makePostAJAXCall('/api/repositories/deployments?environment=' + $("#input-execution-environment-mode").val() + '&numberOfMinutes=' + $("#input-number-of-minutes").val(), null, recentDeploymentsPage.retrieveRecentDeployments);
    });

    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let recentDeploymentsPage =
{
    loadRecentDeploymentsTableComponent : async function () {
        const componentPromises = [
            orionCommon.loadComponent('imports/recentDeployments/recentDeploymentsTable.html', 'recent-deployments-table-area')
        ];

        await Promise.all(componentPromises);
    },


    retrieveRecentDeployments : function(response)
    {
        response.data.data.recentDeployments.forEach(recentDeployment => {
            let tableBodyHTML = '<tr>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="http://localhost:8081/repoDetails?repo=' + recentDeployment.repositoryName + '" target="_blank">' + recentDeployment.repositoryName + '</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + recentDeployment.pipelineURL + '" target="_blank">' + recentDeployment.pipelineURL + '</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="' + recentDeployment.commitURL + '" target="_blank">' + recentDeployment.commitURL + '</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '</tr>';
            $("#recent-deployments-table-body").append(tableBodyHTML);
        });

        recentDeploymentsPage.renderRecentDeploymentTableData();
        orionCommon.disablePreloaderOfElement('#retrieve-recent-deployments-button');
    },


    renderRecentDeploymentTableData : function()
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

        recentDeploymentsTable = new DataTable('#recent-deployments-table', options);
        $(window).trigger('resize');
    }
};