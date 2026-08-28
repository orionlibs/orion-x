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
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let thingsPage =
{
};
