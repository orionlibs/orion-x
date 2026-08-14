window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let reposTable;


$(document).ready(function()
{
    $('body').on('click', '#load-repos-table-using-live-data-button', function(e)
    {
        e.preventDefault();
        orionCommon.enablePreloaderForElement("#load-repos-table-using-live-data-button", "Loading... It will take several minutes");
        reposTable.clear().destroy();
        orionCommon.makePostAJAXCall('/api/repositories?dataToUse=LIVE', null, reposPage.loadReposTable);
    });


    reposPage.loadReposTableComponent();
    orionCommon.makePostAJAXCall('/api/repositories', null, reposPage.loadReposTable);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let reposPage =
{
    loadReposTableComponent : async function () {
        const componentPromises = [
            orionCommon.loadComponent('imports/repos/reposTable.html', 'repos-table-area')
        ];

        await Promise.all(componentPromises);
    },


    loadReposTable : function(response)
    {
        response.data.repositories.forEach(repo => {
            let tableBodyHTML = '<tr>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="http://localhost:8081/repoDetails?repo=' + repo.name + '" target="_blank">' + repo.fullName + '</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">' + repo.owner + '</td>';
            tableBodyHTML += '<td class="align-middle">';

            if(repo.hasE2ETests)
            {
                tableBodyHTML += '<span class="badge border border-success text-success px-2 pt-5px pb-5px rounded fs-12px d-inline-flex align-items-center"><i class="fa fa-circle fs-9px fa-fw me-5px"></i> Has E2E Tests</span>';
            }
            else
            {
                tableBodyHTML += '<span class="badge border border-danger text-danger px-2 pt-5px pb-5px rounded fs-12px d-inline-flex align-items-center"><i class="fa fa-circle fs-9px fa-fw me-5px"></i> No E2E Tests</span>';
            }

            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a id="open-readme-file-content-' + repo.name + '" href="#" data-bs-toggle="modal" data-bs-target="#repoReadmeModal">Open README File</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a id="open-pipeline-configuration-file-content-' + repo.name + '" href="#" data-bs-toggle="modal" data-bs-target="#repoConfigurationFileModal">Open Pipeline Configuration File</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '</tr>';
            $("#repos-table-body").append(tableBodyHTML);


            $('body').on('click', '#open-readme-file-content-' + repo.name, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + repo.name + '/readme', reposPage.loadReadmeFileContent);
            });


            $('body').on('click', '#open-pipeline-configuration-file-content-' + repo.name, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + repo.name + '/pipeline-configuration', reposPage.loadPipelineConfigurationFileContent);
            });
        });

        orionCommon.disablePreloaderOfElement("#load-repos-table-using-live-data-button");
        reposPage.renderReposTableData();
    },


    loadReadmeFileContent : function(response)
    {
        $("#repo-readme-file-content-modal-body").html(response.data.fileContent);
    },


    loadPipelineConfigurationFileContent : function(response)
    {
        $("#repo-pipeline-configuration-file-content-modal-body").html(response.data.fileContent);
    },


    renderReposTableData : function()
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

        reposTable = new DataTable('#repos-table', options);
        $(window).trigger('resize');
    }
};