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
    $('body').on('click', '#create-thing-button', function(e)
    {
        e.preventDefault();

        let metadata = {};

        try
        {
            metadata = JSON.parse($('#input-metadata').val() || '{}');
        }
        catch(error)
        {
            metadata = {};
        }

        let dataToSend = {
            name: $('#input-name').val(),
            description: $('#input-description').val(),
            thingType: $('#input-thing-type').val(),
            parentId: $('#input-parent-id').val() || null,
            unsTopic: $('#input-uns-topic').val(),
            status: $('#input-status').val(),
            registrationMethod: $('#input-registration-method').val(),
            mqttClientId: $('#input-mqtt-client-id').val() || null,
            metadata: metadata
        };

        orionCommon.enablePreloaderForElement("#create-thing-button", "Creating...");
        orionCommon.makePostAJAXCall('/api/things', dataToSend, createThingPage.processThingCreated, createThingPage.processThingCreationFailed);
    });


    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let createThingPage =
{
    processThingCreated : function(response)
    {
        orionCommon.disablePreloaderOfElement("#create-thing-button");
        orionCommon.showNotification(null, 'Thing created!', 3000);
    },


    processThingCreationFailed : function(responseOrError)
    {
        orionCommon.disablePreloaderOfElement("#create-thing-button");

        if(typeof Response !== "undefined" && responseOrError instanceof Response)
        {
            responseOrError.json().then(function(errorBody)
            {
                orionCommon.showNotification('Error!', errorBody.error, 6000);
            }).catch(function()
            {
                orionCommon.showNotification('Error!', 'Unknown error', 6000);
            });
        }
    }
};
