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
    orionCommon.makeGetAJAXCall('/api/repositories/count', homePage.showNumberOfRepos);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let homePage =
{
    showNumberOfRepos : function(response)
    {
        $("#number-of-repos-LED-gauge").sevenSeg({
            digits:Math.abs(response.data.numberOfRepositories).toString().length,
            value:response.data.numberOfRepositories,
            colorOff: "#003200",
            colorOn: "Lime",
            slant: 0
        });
    }
};