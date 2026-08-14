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
    let myElement = document.getElementById('help-articles');
    new SimpleBar(myElement, { autoHide: true });


    $('body').on('click', '#submit-support-question-button', function(e)
    {
        e.preventDefault();
        let dataToSend = {};
        dataToSend["name"] = $("#input-support-name").val();
        dataToSend["question"] = $("#input-support-question").val();
        orionCommon.makePostAJAXCall('/api/support-questions', dataToSend, helpPage.submitSupportQuestion, helpPage.submitSupportQuestion);
    });

    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let helpPage =
{
    submitSupportQuestion : function(response)
    {
        $("#input-support-name").val("");
        $("#input-support-question").val("");
        orionCommon.showNotification("Thanks!", "Question submitted", 3000);
    }
};