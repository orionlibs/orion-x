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
    orionCommon.makeGetAJAXCall('/api/blames/calendar?numberOfDays=60', blameCalendarPage.loadBlameCalendar);
    stompClient = orionCommon.connectToWebsocket('/websocket', '/topic/alarms', reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, orionCommon.handleAlarm);
});


let blameCalendarPage =
{
    loadBlameCalendar : function(response)
    {
        let failedTestRunsEventsList = [];
        let fixedTestRunsEventsList = [];
        let eventID = 0;

        response.data.blameCalendarData.forEach(blame => {
            eventID++;
            if(blame.isTestRunFixed === undefined || blame.isTestRunFixed === null)
            {
                failedTestRunsEventsList.push({
                    id: eventID.toString(),
                    start: blame.failureDateTime,
                    end: blame.failureDateTime,
                    title: '--' + blame.name + ' -- ' + blame.failedRepo + ' (' + blame.environment + ' - ' + blame.branch + ')',
                    description: blame.name + ' -- ' + blame.failedRepo + ' (' + blame.environment + ' - ' + blame.branch + ')'
                });
            }
            else
            {
                fixedTestRunsEventsList.push({
                    id: eventID.toString(),
                    start: blame.testRunFixDateTime,
                    end: blame.testRunFixDateTime,
                    title: '--' + blame.name + ' -- ' + blame.failedRepo + ' -- Fixed after ' + blame.fixedAfter + ' (' + blame.environment + ' - ' + blame.branch + ')',
                    description: blame.name + ' -- ' + blame.failedRepo + ' -- Fixed after ' + blame.fixedAfter + ' (' + blame.environment + ' - ' + blame.branch + ')'
                });
            }
        });

        let failedTestRunsEvents = {
            id: 1,
            className: "bg-danger-transparent", //bg-success
            textColor: '#fff',
            events: failedTestRunsEventsList
        };

        let fixedTestRunsEvents = {
            id: 2,
            className: "bg-success-transparent",
            textColor: '#fff',
            events: fixedTestRunsEventsList
        };

        let calendarElement = document.getElementById('blame-calendar');
        let calendar = new FullCalendar.Calendar(calendarElement, {
            /*headerToolbar: {
                left: 'prev,next today',
                center: 'title',
                right: 'dayGridMonth,timeGridWeek,timeGridDay,listWeek'
            },*/
            defaultView: 'month',
            navLinks: false, // can click day/week names to navigate views
            businessHours: false, // display business hours
            editable: false,
            selectable: false,
            selectMirror: false,
            droppable: false, // this allows things to be dropped onto the calendar

            /*select: function (arg) {
                var title = prompt('Event Title:');
                if (title) {
                    calendar.addEvent({
                        title: title,
                        start: arg.start,
                        end: arg.end,
                        allDay: arg.allDay
                    })
                }
                calendar.unselect()
            },*/
            /*eventClick: function (arg) {
                if (confirm('Are you sure you want to delete this event?')) {
                    arg.event.remove()
                }
            },*/

            eventDidMount: function(info) {
                new bootstrap.Tooltip(info.el, {
                    title: info.event.extendedProps.description || "No additional details",
                    placement: 'top',
                    trigger: 'hover',
                    container: 'body'
                });
            },
            dayMaxEvents: true, // allow "more" link when too many events
            eventSources: [failedTestRunsEvents, fixedTestRunsEvents]
        });

        calendar.render();
    }
};