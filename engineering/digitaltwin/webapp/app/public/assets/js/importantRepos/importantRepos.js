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
    orionCommon.makeGetAJAXCall('/api/repositories/important', importantReposPage.loadImportantReposGroups);
    orionCommon.makeGetAJAXCall('/api/repositories/names', importantReposPage.loadReposMultiselector);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let importantReposPage =
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
            let dataToSend = {};
            dataToSend["repositories"] = selectedRepos;
            orionCommon.makePostAJAXCall('/api/repositories/important', dataToSend, importantReposPage.createRepositoryGroup);
        });
    },


    createRepositoryGroup : function(response)
    {
        orionCommon.showNotification("Saved!", "Important repository group created", 3000);
        repoMultiselector.removeActiveItems();
        $("#important-repositories-area").html("");
        orionCommon.makeGetAJAXCall('/api/repositories/important', importantReposPage.loadImportantReposGroups);
    },


    loadImportantReposGroups : function(response)
    {
        if(response.data.group !== undefined && response.data.group !== null)
        {
            let groupHTML = `
                <div id="repo-group-area" class="card custom-card">
                    <div class="top-left"></div>
                    <div class="top-right"></div>
                    <div class="bottom-left"></div>
                    <div class="bottom-right"></div>
                    <div class="card-header d-flex align-items-center justify-content-between">
                        <div class="card-title mb-0">Important Repositories</div>
                        <a id="delete-repo-group-button" href="#" class="btn btn-danger btn-sm btn-rounded px-3 fw-600 rounded ms-auto">Delete</a>
                    </div>
                    <div class="card-body">
                        <ul class="list-group">
            `;

            let repoHTML = '';

            response.data.group.repositories.forEach(repo => {
                repoHTML += '<li class="list-group-item list-group-item-info"><a href="/repoDetails?repo=' + repo + '" class="pe-auto text-primary fw-medium text-decoration-underline">' + repo + '</a></li>';
            });

            groupHTML += repoHTML + '</ul></div></div>';
            $("#important-repositories-area").append(groupHTML);


            $('body').on('click', '#delete-repo-group-button', function(e)
            {
                e.preventDefault();
                orionCommon.makeDeleteAJAXCall('/api/repositories/important', importantReposPage.deleteGroup, importantReposPage.deleteGroup);
            });


            let myElement = document.getElementById('important-repositories-area');
            new SimpleBar(myElement, { autoHide: true });
        }

        if(response.data.group !== undefined
            && response.data.group !== null
            && response.data.group.repositories !== undefined
            && response.data.group.repositories !== null)
        {
            $("#create-repository-area").addClass("hidden");
        }
    },


    deleteGroup : function(response)
    {
        $("#important-repositories-area").remove();
        $("#create-repository-area").removeClass("hidden");
        orionCommon.showNotification("Deleted!", "Important repository group deleted", 3000);
    }
};