window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let repoMultiselector;


$(document).ready(function()
{
    orionCommon.makeGetAJAXCall('/api/repositories/sequences', repoSequencePage.loadRepoGroups);
    orionCommon.makeGetAJAXCall('/api/repositories/names', repoSequencePage.loadReposMultiselector);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let repoSequencePage =
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

        $('body').on('click', '#create-repo-sequence-button', function(e)
        {
            const selectedRepos = $('#repo-multiselector').val();
            let dataToSend = {};
            dataToSend["name"] = $("#input-repo-sequence-name").val();
            dataToSend["repositories"] = selectedRepos;
            orionCommon.makePostAJAXCall('/api/repositories/sequences', dataToSend, repoSequencePage.createRepositoryGroup);
        });
    },


    createRepositoryGroup : function(response)
    {
        orionCommon.showNotification("Saved!", "Repository sequence created", 3000);
        $("#input-repo-sequence-name").val("");
        repoMultiselector.removeActiveItems();
        $("#repository-groups-area").html("");
        orionCommon.makeGetAJAXCall('/api/repositories/sequences', repoSequencePage.loadRepoGroups);
    },


    deleteGroup : function(response)
    {
        $("#repo-sequence-area-" + response.data.groupID).remove();
        orionCommon.showNotification("Deleted!", "Repository sequence deleted", 3000);
    },


    loadRepoGroups : function(response)
    {
        response.data.groups.forEach(group => {
            let groupHTML = `
                <div id="repo-sequence-area-${group.groupID}" class="card custom-card">
                    <div class="top-left"></div>
                    <div class="top-right"></div>
                    <div class="bottom-left"></div>
                    <div class="bottom-right"></div>
                    <div class="card-header d-flex align-items-center justify-content-between">
                        <div class="card-title mb-0">
                            <a href="/sequentialPipelineRun?repoSequence=${group.groupID}" class="pe-auto text-primary fw-medium text-decoration-underline">${group.name}</a>
                        </div>
                    </div>
                    <div class="card-body">
                        <div class="d-flex align-items-center gap-2">
                            <a id="delete-repo-sequence-button-${group.groupID}" href="#" class="btn btn-danger btn-sm btn-rounded px-3 fw-600 rounded ms-auto">Delete</a>
                        </div>
                        
                        <br>
                        <ul class="list-group">
            `;

            let repoHTML = '';

            group.repositories.forEach(repo => {
                repoHTML += '<li class="list-group-item list-group-item-info"><a href="/repoDetails?repo=' + repo + '" class="pe-auto text-primary fw-medium text-decoration-underline">' + repo + '</a></li>';
            });

            groupHTML += repoHTML + '</ul></div></div>';
            $("#repository-sequence-area").append(groupHTML);


            $('body').on('click', '#delete-repo-sequence-button-' + group.groupID, function(e)
            {
                e.preventDefault();
                orionCommon.makeDeleteAJAXCall('/api/repositories/sequences/' + group.groupID, repoSequencePage.deleteGroup, repoSequencePage.deleteGroup);
            });
        });

        let myElement = document.getElementById('repository-sequence-area');
        new SimpleBar(myElement, { autoHide: true });
    }
};