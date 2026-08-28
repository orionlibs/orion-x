window.onload = async function () {
    orionCommon.loadStandardComponents('');
};


let reconnectAttempts = 0;
const maxReconnectAttempts = 1000000;
const reconnectDelay = 10000;
let reconnectTimer = null;
let stompClient = null;
let thingsTable;


$(document).ready(function()
{
    orionCommon.makeGetAJAXCall('/api/things/summaries', thingsPage.loadThingsTable);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let thingsPage =
{
    loadThingsTable : function(response)
    {
        response.things.forEach(thing => {
            let tableBodyHTML = '<tr>';
            tableBodyHTML += '<td class="align-middle">' + thing.name + '</td>';
            tableBodyHTML += '<td class="align-middle">' + thing.description + '</td>';
            tableBodyHTML += '<td class="align-middle">' + thing.createdAt + '</td>';
            tableBodyHTML += '<td class="align-middle">' + thing.updatedAt + '</td>';
            tableBodyHTML += '</tr>';
            $("#things-table-body").append(tableBodyHTML);
        });

        thingsTable = orionCommon.renderDataTable('things-table');
    }
};
