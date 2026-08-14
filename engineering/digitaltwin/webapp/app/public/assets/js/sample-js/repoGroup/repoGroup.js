window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let repoMultiselector;
let runTestsButtonID;


$(document).ready(function()
{
    orionCommon.makeGetAJAXCall('/api/repositories/groups', repoGroupPage.loadRepoGroups);
    orionCommon.makeGetAJAXCall('/api/repositories/names', repoGroupPage.loadReposMultiselector);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let repoGroupPage =
{
    loadReposMultiselector : function(response)
    {
        response.data.repositories.forEach(repo => {
            let multiselectorHTML = '<option value="' + repo.name + '">' + repo.name + '</option>';
            $("#repo-multiselector").append(multiselectorHTML);
        });

        repoMultiselector = new Choices(
            '#repo-multiselector',
            {
                allowHTML: true,
                removeItemButton: true,
            }
        );

        $('body').on('click', '#create-repo-group-button', function(e)
        {
            const selectedRepos = $('#repo-multiselector').val();
            const isTeamType = $('#group-type-switch').is(':checked');
            let dataToSend = {};
            dataToSend["name"] = $("#input-repo-group-name").val();
            dataToSend["description"] = $("#input-repo-group-description").val();
            dataToSend["isTeamType"] = isTeamType;
            dataToSend["repositories"] = selectedRepos;
            orionCommon.makePostAJAXCall('/api/repositories/groups', dataToSend, repoGroupPage.createRepositoryGroup);
        });
    },


    createRepositoryGroup : function(response)
    {
        orionCommon.showNotification("Saved!", "Repository group created", 3000);
        $("#input-repo-group-name").val("");
        $("#input-repo-group-description").val("");
        repoMultiselector.removeActiveItems();
        $("#repository-groups-area").html("");
        orionCommon.makeGetAJAXCall('/api/repositories/groups', repoGroupPage.loadRepoGroups);
    },


    deleteGroup : function(response)
    {
        $("#repo-group-area-" + response.data.groupID).remove();
        orionCommon.showNotification("Deleted!", "Repository group deleted", 3000);
    },


    loadRepoGroups : function(response)
    {
        response.data.groups.forEach(group => {
            let groupType = 'Simple Group';

            if(group.isTeamType)
            {
                groupType = 'Team Group';
            }

            let groupHTML = `
                <div id="repo-group-area-${group.groupID}" class="card custom-card">
                    <div class="top-left"></div>
                    <div class="top-right"></div>
                    <div class="bottom-left"></div>
                    <div class="bottom-right"></div>
                    <div class="card-header d-flex align-items-center justify-content-between">
                        <div class="card-title mb-0">
                            ${group.name} -- ${groupType}
                        </div>
                    </div>
                    <div class="card-body">
                        <span>${group.description}</span>
                        <br><br>
                        
                        <div class="d-flex align-items-center gap-2">
                            <a id="run-repo-group-tests-button-${group.groupID}" href="#" class="btn btn-info btn-sm btn-rounded px-3 fw-600 rounded text-nowrap">Run Tests</a>
                            <input id="input-execution-environment-mode-${group.groupID}" type="hidden" value="DEVELOPMENT"/>
                            <select id="input-execution-environment-mode-dropdown-${group.groupID}" class="form-select table-dropdown form-select-sm w-auto">
                                <option value="DEVELOPMENT" selected>development</option>
                                <option value="STAGING">staging</option>
                            </select>
                            <a id="delete-repo-group-button-${group.groupID}" href="#" class="btn btn-danger btn-sm btn-rounded px-3 fw-600 rounded ms-auto">Delete</a>
                        </div>
                        
                        <br>
                        <ul class="list-group">
            `;

            let repoHTML = '';

            group.repositories.forEach(repo => {
                repoHTML += '<li class="list-group-item list-group-item-info"><a href="/repoDetails?repo=' + repo + '" class="pe-auto text-primary fw-medium text-decoration-underline">' + repo + '</a></li>';
            });

            groupHTML += repoHTML + '</ul></div></div>';
            $("#repository-groups-area").append(groupHTML);


            $("#input-execution-environment-mode-dropdown-" + group.groupID).on("change", function () {
                let selectedValue = $(this).val();
                $("#input-execution-environment-mode-" + group.groupID).val(selectedValue);
            });


            $('body').on('click', '#run-repo-group-tests-button-' + group.groupID, function(e)
            {
                e.preventDefault();
                runTestsButtonID = "#" + $(this).attr('id');
                orionCommon.enablePreloaderForElement(runTestsButtonID, "Wait...");
                orionCommon.makePostAJAXCall('/api/repositories/groups/' + group.groupID + '/pipelines/commands/tests/executions?environment=' + $("#input-execution-environment-mode-" + group.groupID).val(), null, repoGroupPage.processTestsExecution);
            });


            $('body').on('click', '#delete-repo-group-button-' + group.groupID, function(e)
            {
                e.preventDefault();
                orionCommon.makeDeleteAJAXCall('/api/repositories/groups/' + group.groupID, repoGroupPage.deleteGroup, repoGroupPage.deleteGroup);
            });
        });

        let myElement = document.getElementById('repository-groups-area');
        new SimpleBar(myElement, { autoHide: true });
    },


    processTestsExecution : function(response)
    {
        orionCommon.disablePreloaderOfElement(runTestsButtonID);
        orionCommon.showNotification('Running!', 'Tests running. Manage the test runs in the individual repository details pages', null);
    }
};