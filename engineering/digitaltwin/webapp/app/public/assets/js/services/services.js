window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let servicesTable;


$(document).ready(function()
{
    servicesPage.loadReposTableComponent();
    orionCommon.makeGetAJAXCall('/api/services', servicesPage.loadServicesTable);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let servicesPage =
{
    loadReposTableComponent : async function () {
        const componentPromises = [
            orionCommon.loadComponent('imports/services/servicesTable.html', 'services-table-area')
        ];

        await Promise.all(componentPromises);
    },


    loadServicesTable : function(response)
    {
        console.log(response.data.services);
        response.data.services.forEach(service => {
            let tableBodyHTML = '<tr>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a href="http://localhost:8081/repoDetails?repo=' + service.name + '" target="_blank">' + service.name + '</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td id="service-owner-' + service.name + '" class="align-middle">' + service.owner + '&nbsp;&nbsp;&nbsp;<button id="change-repo-owner-button-' + service.name + '" type="button" class="btn btn-success-light rounded-pill btn-wave" data-bs-toggle="modal" data-bs-target="#changeRepoOwnerModal">Change</button></td>';

            tableBodyHTML += '<td class="align-middle">';

            if(service.hasE2ETests)
            {
                tableBodyHTML += '<span class="badge border border-success text-success px-2 pt-5px pb-5px rounded fs-12px d-inline-flex align-items-center"><i class="fa fa-circle fs-9px fa-fw me-5px"></i> Has E2E Tests</span>';
            }
            else
            {
                tableBodyHTML += '<span class="badge border border-danger text-danger px-2 pt-5px pb-5px rounded fs-12px d-inline-flex align-items-center"><i class="fa fa-circle fs-9px fa-fw me-5px"></i> No E2E Tests</span>';
            }

            tableBodyHTML += '</td>';

            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a id="open-readme-file-content-' + service.name + '" href="#" data-bs-toggle="modal" data-bs-target="#repoReadmeModal">Open README File</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '<td class="align-middle">';
            tableBodyHTML += '<a id="open-pipeline-configuration-file-content-' + service.name + '" href="#" data-bs-toggle="modal" data-bs-target="#repoConfigurationFileModal">Open Pipeline Configuration File</a>';
            tableBodyHTML += '</td>';
            tableBodyHTML += '</tr>';
            $("#services-table-body").append(tableBodyHTML);
            $("#input-repo-owner").val(service.owner);


            $('body').on('click', '#change-repo-owner-button-' + service.name, function(e)
            {
                e.preventDefault();
                $("#input-repo-name").val(service.name);
            });


            $('body').on('click', '#open-readme-file-content-' + service.name, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + service.name + '/readme', servicesPage.loadReadmeFileContent);
            });


            $('body').on('click', '#open-pipeline-configuration-file-content-' + service.name, function(e)
            {
                orionCommon.makeGetAJAXCall('/api/repositories/' + service.name + '/pipeline-configuration', servicesPage.loadPipelineConfigurationFileContent);
            });
        });

        $('body').on('click', '#save-repo-owner-button', function(e)
        {
            let repoName = $("#input-repo-name").val();
            let dataToSend = {};
            dataToSend["owner"] = $("#input-repo-owner").val();
            orionCommon.makePutAJAXCall('/api/repositories/' + repoName + '/owners', dataToSend, servicesPage.updateRepoOwner);
        });

        servicesPage.renderServicesTableData();
    },


    updateRepoOwner : function(response)
    {
        $("#service-owner-" + response.data.repositoryName).html(response.data.owner);
        $("#input-repo-owner").val('');
        orionCommon.showNotification("Saved!", response.data.message, 3000);
        const modalElement = document.getElementById('changeRepoOwnerModal');
        const modalInstance = bootstrap.Modal.getInstance(modalElement);

        if (modalInstance) {
            modalInstance.hide();
        }
    },


    loadReadmeFileContent : function(response)
    {
        $("#repo-readme-file-content-modal-body").html(response.data.fileContent);
    },


    loadPipelineConfigurationFileContent : function(response)
    {
        $("#repo-pipeline-configuration-file-content-modal-body").html(response.data.fileContent);
    },


    renderServicesTableData : function()
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

        servicesTable = new DataTable('#services-table', options);
        $(window).trigger('resize');
    }
};