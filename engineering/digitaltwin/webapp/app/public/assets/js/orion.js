$(window).on('resize', function () {
    $('.table').each(function() {
        if ($.fn.DataTable.isDataTable(this)) {
            $(this).DataTable().columns.adjust();
        }
    });
});


let alarmIDsShownToUser = {};


let orionCommon = {
    handleAlarm : function(message)
    {
        if(alarmIDsShownToUser[message.alarmID])
        {
            //do nothing
        }
        else
        {
            orionCommon.showNotification("Alert!", message.alarmMessage, null);
            /*let alertHTML = `
                <div class="alert alert-danger alert-dismissible fade show d-flex flex-column">
                    <div>${message.alarmMessage}</div>
                    <br>
                    <button type="button" data-bs-dismiss="alert" aria-label="Close">Close</button>
                </div>
            `;*/
            /*alertDiv.innerHTML = `
                <div>${message.alarmMessage}</div>
                <div class="mt-2">
                    <button id="acknowledge-alarm-event-id-${message.alarmEventID}" class="btn btn-sm btn-outline-light me-2 acknowledge-btn">Acknowledge</button>
                    <button id="disable-alarm-for-alarm-id-${message.alarmID}" class="btn btn-sm btn-outline-warning disable-btn">Disable</button>
                </div>
                <button id="alarm-close-button-alarm-event-id-${message.alarmEventID}" type="button" class="btn-close position-absolute top-0 end-0" data-bs-dismiss="alert" aria-label="Close"></button>
            `;*/

            //$("#floating-alarms-container").append(alertHTML);
            alarmIDsShownToUser[message.alarmID] = true;

            /*$('body').on('click', '#acknowledge-alarm-event-id-' + message.alarmEventID, function(e)
            {
                orionCommon.makePutAJAXCall('/api/alarms/events/' + message.alarmEventID + '/acknowledgements', null, orionCommon.processAlarmAcknowledgement);
            });

            $('body').on('click', '#disable-alarm-for-alarm-id-' + message.alarmID, function(e)
            {
                orionCommon.makePutAJAXCall('/api/alarms/' + message.alarmID + '/disablements/events/' + message.alarmEventID, null, orionCommon.processAlarmDisablement);
            });*/
        }
    },


    loadStandardComponents: async function(prefixForComponentURLs) {
        const componentPromises = [
            orionCommon.loadComponent(prefixForComponentURLs + '/imports/HTMLHeader.html', 'html-header'),
            orionCommon.loadComponent(prefixForComponentURLs + '/imports/header.html', 'header'),
            orionCommon.loadComponent(prefixForComponentURLs + '/imports/sidebar.html', 'sidebar'),
            orionCommon.loadComponent(prefixForComponentURLs + '/imports/footer.html', 'footer')
        ];

        await Promise.all(componentPromises);
    },


    makeGetAJAXCall: function(url, callbackSuccessFunction, callbackFailFunction) {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 60000);

        fetch(url, {
            method: 'GET',
            cache: "no-cache",
            mode: "cors", //cors, no-cors, same-origin
            credentials: "omit", //include, same-origin, omit
            headers: {
                'Content-Type': 'application/json',
                "Connection": "keep-alive"
                //'X-Xsrf-Token': orionCommon.getCookie('XSRF-TOKEN')
            },
            signal: controller.signal
        })
            .then(response => {
                if (!response.ok) {
                    if (callbackFailFunction) {
                        callbackFailFunction(response);
                    } else {
                        alert('Problem with fetch operation: ' + response.statusText);
                        throw new Error('Error ' + response.statusText);
                    }
                }

                return response.json();
            })
            .then(jsonResponse => {
                if (callbackSuccessFunction) {
                    callbackSuccessFunction(jsonResponse);
                }
            })
            .catch(error => {
                if (callbackFailFunction) {
                    callbackFailFunction(error);
                } else {
                    alert('Problem with fetch operation: ' + error);
                    throw new Error('Problem with fetch operation: ' + error);
                }
            })
            .finally(() => clearTimeout(timeoutId));
    },


    makePostAJAXCall: function(url, dataToSend, callbackSuccessFunction, callbackFailFunction) {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 60000);

        fetch(url, {
            method: 'POST',
            cache: "no-cache",
            mode: "cors", //cors, no-cors, same-origin
            credentials: "omit", //include, same-origin, omit
            headers: {
                'Content-Type': 'application/json',
                "Connection": "keep-alive"
                //'X-Xsrf-Token': orionCommon.getCookie('XSRF-TOKEN')
            },
            body: JSON.stringify(dataToSend),
            signal: controller.signal
        })
            .then(response => {
                if (!response.ok) {
                    if (callbackFailFunction) {
                        callbackFailFunction(response);
                    } else {
                        alert('Problem with fetch operation: ' + response.statusText);
                        throw new Error('Error ' + response.statusText);
                    }
                }

                return response.json();
            })
            .then(jsonResponse => {
                if (callbackSuccessFunction) {
                    callbackSuccessFunction(jsonResponse);
                }
            })
            .catch(error => {
                if (callbackFailFunction) {
                    callbackFailFunction(error);
                } else {
                    alert('Problem with fetch operation: ' + error);
                    throw new Error('Problem with fetch operation: ' + error);
                }
            })
            .finally(() => clearTimeout(timeoutId));
    },


    makePutAJAXCall: function(url, dataToSend, callbackSuccessFunction, callbackFailFunction) {
        fetch(url, {
            method: 'PUT',
            cache: "no-cache",
            mode: "cors", //cors, no-cors, same-origin
            credentials: "omit", //include, same-origin, omit
            headers: {
                'Content-Type': 'application/json' //,
                //'X-Xsrf-Token': orionCommon.getCookie('XSRF-TOKEN')
            },
            body: JSON.stringify(dataToSend)
        })
            .then(response => {
                if (!response.ok) {
                    if (callbackFailFunction) {
                        callbackFailFunction(response);
                    } else {
                        alert('Problem with fetch operation: ' + response.statusText);
                        throw new Error('Error ' + response.statusText);
                    }
                }

                return response.json();
            })
            .then(jsonResponse => {
                if (callbackSuccessFunction) {
                    callbackSuccessFunction(jsonResponse);
                }
            })
            .catch(error => {
                if (callbackFailFunction) {
                    callbackFailFunction(error);
                } else {
                    alert('Problem with fetch operation: ' + error);
                    throw new Error('Problem with fetch operation: ' + error);
                }
            });
    },


    makeDeleteAJAXCall: function(url, callbackSuccessFunction, callbackFailFunction) {
        fetch(url, {
            method: 'DELETE',
            cache: "no-cache",
            mode: "cors", //cors, no-cors, same-origin
            credentials: "omit", //include, same-origin, omit
            headers: {
                'Content-Type': 'application/json' //,
                //'X-Xsrf-Token': orionCommon.getCookie('XSRF-TOKEN')
            }
        })
            .then(response => {
                if (!response.ok) {
                    if (callbackFailFunction) {
                        callbackFailFunction(response);
                    } else {
                        alert('Problem with fetch operation: ' + response.statusText);
                        throw new Error('Error ' + response.statusText);
                    }
                }

                return response.json();
            })
            .then(jsonResponse => {
                if (callbackSuccessFunction) {
                    callbackSuccessFunction(jsonResponse);
                }
            })
            .catch(error => {
                if (callbackFailFunction) {
                    callbackFailFunction(error);
                } else {
                    alert('Problem with fetch operation: ' + error);
                    throw new Error('Problem with fetch operation: ' + error);
                }
            });
    },


    uploadFile: async function(URL, fileInputID, callbackFunction) {
        const fileInput = document.getElementById(fileInputID);
        const files = fileInput.files;
        const formData = new FormData();

        for (let file of files) {
            formData.append('files[]', file);
        }

        try {
            const response = await fetch(URL, {
                method: 'POST',
                body: formData,
            });

            if (response.ok) {
                const result = await response.json();

                if (typeof callbackFunction === "function") {
                    callbackFunction(result);
                }
            } else {
                if (typeof callbackFunction === "function") {
                    callbackFunction(response.statusText);
                }
            }
        } catch (error) {
            if (typeof callbackFunction === "function") {
                callbackFunction(error);
            }
            else
            {
                alert('Problem with fetch operation: ' + error);
                throw new Error('Problem with fetch operation: ' + error);
            }
        }
    },


    createFormToDownloadFile: function(URLOfFileToDownload) {
        let downloadButton = document.createElement("form");
        downloadButton.setAttribute("method", "post");
        downloadButton.setAttribute("action", URLOfFileToDownload);
        document.body.appendChild(downloadButton);
        downloadButton.submit();
        document.body.removeChild(downloadButton);
    },


    bindDownloadButtonAndCreateFormToDownloadFile: function(IDOfDownloadButton, URLOfFileToDownload) {
        $('body').on('click', '#' + IDOfDownloadButton, function(e) {
            orionCommon.createFormToDownloadFile(URLOfFileToDownload);
        });
    },


    getCookie: function(name) {
        const cookieValue = document.cookie
            .split('; ')
            .find(cookie => cookie.startsWith(name + '='))
            ?.split('=')[1];
        return cookieValue ? decodeURIComponent(cookieValue) : null;
    },


    loadComponentDataAsText: function(url, elementID) {
        fetch(url, {
            method: 'GET',
            cache: "no-cache",
            mode: "cors",
            credentials: "omit",
            headers: {
                'Content-Type': 'application/json'
            }
        })
            .then(response => {
                if (!response.ok) {
                    alert('Problem with fetch operation: ' + response.statusText);
                    throw new Error('Network response was not ok ' + response.statusText);
                }

                return response.text();
            })
            .then(data => {
                orionCommon.updateComponent(elementID, data);
            })
            .catch(error => {
                document.getElementById(elementID).innerHTML = 'Failed to load data:' + error;
            });
    },


    updateComponent: function(elementID, data) {
        const element = document.getElementById(elementID);
        element.innerHTML = data;
    },


    updateComponentValue: function(elementID, data) {
        const element = document.getElementById(elementID);
        element.value = data;
    },


    connectToWebsocket: function(websocketURL, topicToSubscribeTo, reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, callbackSuccessFunction) {
        let socket = new SockJS(websocketURL);
        socket.onerror = function(error) {
            console.error('SockJS connection error:', error);
        };
        let stompClient = Stomp.over(socket);
        stompClient.debug = null;
        stompClient.heartbeat.outgoing = 0;
        stompClient.heartbeat.incoming = 0;
        stompClient.reconnect_delay = 1000;

        //stompClient.connect({username: 'user', password: 'password'}, function(frame)
        stompClient.connect({}, function(frame) {
                console.log('Connected: ' + frame);
                reconnectAttempts = 0;
                stompClient.subscribe(topicToSubscribeTo, function(message) {
                    if (message.body) {
                        let messageBody = JSON.parse(message.body);

                        if (callbackSuccessFunction) {
                            callbackSuccessFunction(messageBody);
                        }
                    }
                });

                /*stompClient.send("/app/lastMessage", {}, JSON.stringify({
                    topic: topicToSubscribeTo
                }));*/
            },
            function(error) {
                console.log('Connection error:', error);
                orionCommon.handleWebsocketDisconnection(websocketURL, topicToSubscribeTo, reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, callbackSuccessFunction);
            });

        window.onbeforeunload = function() {
            if (stompClient) {
                stompClient.disconnect();
            }
        };

        return stompClient;
    },


    handleWebsocketDisconnection: function(websocketURL, topicToSubscribeTo, reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, callbackSuccessFunction) {
        if (reconnectAttempts < maxReconnectAttempts) {
            console.log(`Connection lost. Attempting to reconnect in ${reconnectDelay/1000} seconds...`);

            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
            }

            reconnectTimer = setTimeout(() => {
                reconnectAttempts++;
                console.log(`Reconnection attempt ${reconnectAttempts}/${maxReconnectAttempts}`);
                orionCommon.connectToWebsocket(websocketURL, topicToSubscribeTo, reconnectAttempts, maxReconnectAttempts, reconnectDelay, reconnectTimer, callbackSuccessFunction);
            }, reconnectDelay);
        } else {
            console.error('Please refresh the page.');
        }
    },


    loadComponent: async function(componentRelativePath, elementIDToInjectComponent, functionToCallAfterComponentIsLoaded) {
        try {
            const response = await fetch(componentRelativePath);
            const text = await response.text();
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = text;

            tempDiv.querySelectorAll("script").forEach(oldScript => {
                const newScript = document.createElement("script");
                newScript.textContent = oldScript.textContent;
                document.body.appendChild(newScript);
                document.body.removeChild(newScript);
            });

            const template = tempDiv.querySelector('template');
            const clone = document.importNode(template.content, true);
            document.getElementById(elementIDToInjectComponent).appendChild(clone);

            if (typeof window[functionToCallAfterComponentIsLoaded] === "function") {
                requestAnimationFrame(() => {
                    window[functionToCallAfterComponentIsLoaded]();
                });
            }
        } catch (error) {
            alert('Error loading the component:' + error);
            throw new Error('Error loading the component: ' + error);
        }
    },


    loadTemplateComponent: async function(componentRelativePath, elementIDToInjectComponent, appendComponentToElement, data = null, callbackFunction) {
        try {
            const opts = {};
            opts.method = 'POST';
            opts.headers = { 'Content-Type': 'application/json', 'Accept': 'text/html' };

            if(data)
            {
                opts.body = JSON.stringify(data);
            }

            const resp = await fetch(componentRelativePath, opts);
            if (!resp.ok) {
                const txt = await resp.text().catch(()=> '');
                alert('Problem with fetch operation: ' + resp.status);
                throw new Error('Failed to load component: ' + resp.status + ' ' + txt);
            }

            const html = await resp.text();
            const container = document.getElementById(elementIDToInjectComponent);
            //if (!container) throw new Error('Target element not found: ' + elementIDToInjectComponent);

            if(appendComponentToElement)
            {
                container.innerHTML += html;
            }
            else
            {
                container.innerHTML = html;
            }

            // Optionally execute inline scripts inside the returned HTML (if any)
            Array.from(container.querySelectorAll('script')).forEach(oldScript => {
                const newScript = document.createElement('script');
                if (oldScript.src) {
                    newScript.src = oldScript.src;
                } else {
                    newScript.textContent = oldScript.textContent;
                }
                document.head.appendChild(newScript).parentNode.removeChild(newScript);
            });

            if(callbackFunction)
            {
                callbackFunction();
            }
            else
            {
                return container;
            }
        } catch (error) {
            alert('orionCommon.loadTemplateComponent error: ' + error);
            throw new Error('orionCommon.loadTemplateComponent error: ' + error);
        }
    },


    loadTemplates: async function(templateURL, elementIDToInjectComponent, appendComponentToElement, dataToSend, callbackFunction) {
        if(dataToSend === null)
        {
            dataToSend = {};
        }

        const componentPromises = [
            orionCommon.loadTemplateComponent(templateURL, elementIDToInjectComponent, appendComponentToElement, dataToSend, callbackFunction)
        ];

        await Promise.all(componentPromises);
    },


    renderDataTable : function(tableID)
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
            lengthMenu: [[10, 50, 100, 1000, -1], [10, 50, 100, 1000, "All"]]
        };

        let table = new DataTable('#' + tableID, options);
        $(window).trigger('resize');
        return table;
    },


    buildApexChartLineChart: function(dataSeriesName, series, xAxisDataType, maximumDataSeriesValue, yAxisValuePattern, chartContainerID) {
        let options = {
            series: [{
                name: dataSeriesName,
                data: series
            }
            ],
            chart: {
                height: 320,
                type: 'line',
                zoom: {
                    type: 'xy'
                }
            },
            fill: {
                type: 'solid', // Ensure it's not 'pattern' or 'image' which sometimes causes loops
            },
            stroke: {
                curve: 'smooth', // Options: 'smooth', 'straight', 'stepline'
                width: 3,         // Thickness of the connecting line
                connectNulls: true
            },
            markers: {
                size: 5,         // This keeps the "scatter" dots visible
                hover: { size: 7 }
            },
            dataLabels: {
                enabled: true,
                formatter: function (val, { dataPointIndex, w }) {
                    // 1. Get the data array from the configuration
                    const data = w.config.series[0].data;
                    // 2. Hide the label for the first point (no comparison possible)
                    if (dataPointIndex === 0) {
                        return '';
                    }

                    // 3. Get current and previous values
                    const currentVal = val;
                    const prevVal = data[dataPointIndex - 1][1];

                    if (prevVal === 0) return "0%"; // Avoid division by zero

                    // 4. Calculate percentage change
                    const diff = currentVal - prevVal;
                    const percentChange = Math.abs(((diff / prevVal) * 100)).toFixed(0);

                    // 5. Return the string with the arrow
                    if (diff > 0) {
                        return `↑ ${percentChange}%`;
                    }
                    else if (diff < 0) {
                        return `↓ ${percentChange}%`;
                    }

                    return '0%';
                },
                offsetY: -10, // Move the label slightly above the marker
                style: {
                    fontSize: '12px',
                    fontFamily: 'Helvetica, Arial, sans-serif',
                    fontWeight: 'bold',
                    // Note: ApexCharts dataLabels.style.colors applies to the whole series.
                    // To get individual colors (Red/Green), see the 'Alternative' section below.
                    colors: ['#333']
                },
                background: {
                    enabled: true,
                    padding: 4,
                    borderRadius: 2,
                    borderWidth: 1,
                    borderColor: '#fff',
                    opacity: 0.9,
                }
            },
            colors: ["#45d65b"],
            grid: {
                borderColor: '#f2f5f7',
            },
            xaxis: {
                type: xAxisDataType,
                labels: {
                    show: true,
                    style: {
                        colors: "#8c9097",
                        fontSize: '11px',
                        fontWeight: 600,
                        cssClass: 'apexcharts-xaxis-label',
                    },
                }
            },
            yaxis: {
                max: maximumDataSeriesValue,
                labels: {
                    show: true,
                    style: {
                        colors: "#8c9097",
                        fontSize: '11px',
                        fontWeight: 600,
                        cssClass: 'apexcharts-yaxis-label',
                    },
                    formatter: function (val) {
                        return val.toFixed(0) + yAxisValuePattern;
                    }
                }
            }
        };
        let chart = new ApexCharts(document.querySelector(chartContainerID), options);
        chart.render();
    },


    buildApexCharBarChartWithDataLabels: function(chartTitle, seriesLabels, series, chartContainerID) {
        let options = {
            series: [{
                data: series
            }],
            chart: {
                type: 'bar',
                height: 320
            },
            plotOptions: {
                bar: {
                    barHeight: '100%',
                    distributed: true,
                    horizontal: true,
                    dataLabels: {
                        position: 'bottom'
                    },
                }
            },
            colors: ["#00ffbe", "#45d65b", "#f39c12", "#e74c3c", "#3498db", "#fc6c85", "#8f00ff", "#a65e9a", "#2ecc71", "#45d65b"
            ],
            grid: {
                borderColor: '#f2f5f7',
            },
            dataLabels: {
                enabled: true,
                textAnchor: 'start',
                style: {
                    colors: ['#fff']
                },
                formatter: function (val, opt) {
                    return opt.w.globals.labels[opt.dataPointIndex] + ":  " + val
                },
                offsetX: 0,
                dropShadow: {
                    enabled: false
                }
            },
            stroke: {
                width: 1,
                colors: ['#fff']
            },
            xaxis: {
                categories: seriesLabels,
                labels: {
                    show: true,
                    style: {
                        colors: "#8c9097",
                        fontSize: '11px',
                        fontWeight: 600,
                        cssClass: 'apexcharts-xaxis-label',
                    },
                }
            },
            yaxis: {
                labels: {
                    show: false
                }
            },
            title: {
                text: chartTitle,
                align: 'center',
                floating: true,
                style: {
                    fontSize: '13px',
                    fontWeight: 'bold',
                    color: '#8c9097'
                },
            },
            /*subtitle: {
                text: 'Category Names as DataLabels inside bars',
                align: 'center',
            },*/
            tooltip: {
                theme: 'dark',
                x: {
                    show: false
                },
                y: {
                    title: {
                        formatter: function () {
                            return ''
                        }
                    }
                }
            }
        };

        let chart = new ApexCharts(document.querySelector(chartContainerID), options);
        chart.render();
    },


    buildApexCharColumnChart: function(seriesLabels, series, chartContainerID) {
        let colors = ['#00ffbe', '#45d65b', '#f39c12', '#3498db', '#e74c3c', '#2ecc71', '#8f00ff'];

        let options = {
            series: [{
                data: series
            }],
            chart: {
                height: 320,
                type: 'bar',
                events: {
                    click: function (chart, w, e) {
                    }
                }
            },
            colors: ['#00ffbe', '#45d65b', '#f39c12', '#3498db', '#e74c3c', '#2ecc71', '#8f00ff', '#fc6c85'],
            plotOptions: {
                bar: {
                    columnWidth: '45%',
                    distributed: true,
                }
            },
            dataLabels: {
                enabled: false
            },
            legend: {
                show: false
            },
            grid: {
                borderColor: '#f2f5f7',
            },
            xaxis: {
                categories: seriesLabels,
                labels: {
                    style: {
                        colors: colors,
                        fontSize: '12px'
                    }
                }
            },
            yaxis: {
                labels: {
                    show: true,
                    style: {
                        colors: "#8c9097",
                        fontSize: '11px',
                        fontWeight: 600,
                        cssClass: 'apexcharts-yaxis-label',
                    },
                }
            }
        };

        let chart = new ApexCharts(document.querySelector(chartContainerID), options);
        chart.render();
    },


    buildArrayOfRangeOfIntegers : function(numberOfElements, minimumValue, maximumValue) {
        const step = (maximumValue - minimumValue) / (numberOfElements - 1);
        const result = [];

        for (let i = 0; i < numberOfElements; i++) {
            result.push(Math.round(minimumValue + i * step));
        }

        return result;
    },


    disablePreloaderOfElement : function(elementID)
    {
        const $btn = $(elementID);
        $btn.prop('disabled', false).html($btn.data('original-text'));
    },


    enablePreloaderForElement : function(elementID, tempButtonText)
    {
        const $btn = $(elementID);
        $btn.data('original-text', $btn.html());
        $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>' + tempButtonText);
    },


    showNotification : function(title, message, timeoutInMilliseconds) {
        const alertId = 'alert-' + Date.now();
        const alertHtml = `
            <div id="${alertId}" class="alert alert-cyan alert-dismissible fade show" role="alert">
                <div class="d-flex align-items-center">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="currentColor" class="me-2" viewBox="0 0 256 256">
                        <path d="M128,24A104,104,0,1,0,232,128,104.11,104.11,0,0,0,128,24Zm0,192a88,88,0,1,1,88-88A88.1,88.1,0,0,1,128,216Zm16-40a8,8,0,0,1-8,8,16,16,0,0,1-16-16V128a8,8,0,0,1,0-16,16,16,0,0,1,16,16v32A8,8,0,0,1,144,176ZM128,80a12,12,0,1,1,12-12A12,12,0,0,1,128,80Z"></path>
                    </svg>
                    ${message}
                </div>
                <br>
                <button type="button" data-bs-dismiss="alert" aria-label="Close">Close</button>
            </div>
        `;

        $('#floating-notification-container').prepend(alertHtml);

        if(timeoutInMilliseconds)
        {
            setTimeout(() => {
                $(`#${alertId}`).fadeOut(500, function() {
                    $(this).remove();
                });
            }, timeoutInMilliseconds);
        }
    },


    areAllFiltersValid : function(filterCodeToInputIDMap, filterCodeToRequiredMap)
    {
        let areFilterValuesValid = true;

        for (let filterCode in filterCodeToInputIDMap) {
            let inputID = filterCodeToInputIDMap[filterCode];
            let filterValue = $("#" + inputID).val();

            if (filterCodeToRequiredMap && filterCodeToRequiredMap[filterCode]) {
                if(filterValue === null || filterValue.length === 0)
                {
                    areFilterValuesValid = false;
                    $("#" + inputID).addClass("is-invalid");
                }
                else
                {
                    $("#" + inputID).removeClass("is-invalid");
                }
            }
        }

        return areFilterValuesValid;
    },


    areAllRequiredFiltersValid : function(filterCodeToInputIDMap, filterCodeToRequiredMap)
    {
        let areRequiredFilterValuesValid = true;

        for (let filterCode in filterCodeToRequiredMap) {
            let inputID = filterCodeToInputIDMap[filterCode];
            let filterValue = $("#" + inputID).val();

            if (filterCodeToRequiredMap[filterCode]) {
                if(filterValue === null || filterValue.length === 0)
                {
                    areRequiredFilterValuesValid = false;
                    $("#" + inputID).addClass("is-invalid");
                }
                else
                {
                    $("#" + inputID).removeClass("is-invalid");
                }
            }
        }

        return areRequiredFilterValuesValid;
    },


    addFilterValuesToDataToSendObject : function(filterCodeToInputIDMap)
    {
        let dataToSend = {};

        for (let filterCode in filterCodeToInputIDMap) {
            let inputID = filterCodeToInputIDMap[filterCode];
            let filterValue = $("#" + inputID).val();
            dataToSend[filterCode] = filterValue;
        }

        return dataToSend;
    },


    addRequiredFilterValuesToDataToSendObject : function(filterCodeToInputIDMap, filterCodeToRequiredMap)
    {
        let dataToSend = {};

        for (let filterCode in filterCodeToInputIDMap) {
            let inputID = filterCodeToInputIDMap[filterCode];
            let filterValue = $("#" + inputID).val();

            if (filterCodeToRequiredMap[filterCode]) {
                dataToSend[filterCode] = filterValue;
                $("#" + inputID).removeClass("is-invalid");
            }
        }

        return dataToSend;
    },


    extractRequestParameterFromPageURL : function(requestParameterName)
    {
        const params = new URLSearchParams(window.location.search);
        let requestParam = params.get(requestParameterName);

        if(requestParam.endsWith("#"))
        {
            requestParam = requestParam.substring(0, requestParam.length - 1);
        }

        if (!requestParam) {
            alert('No ' + requestParameterName + ' specified');
            return null;
        } else {
            return requestParam;
        }
    },


    extractAllRequestParameters: function() {
        const params = new URLSearchParams(window.location.search);
        let paramMap = {};

        params.forEach((value, key) => {
            let cleanValue = value;

            if (cleanValue && cleanValue.endsWith("#")) {
                cleanValue = cleanValue.substring(0, cleanValue.length - 1);
            }

            paramMap[key] = cleanValue;
        });

        if (Object.keys(paramMap).length === 0) {
            return null;
        }

        return paramMap;
    },


    copyToClipboard : function(valueToCopy)
    {
        navigator.clipboard.writeText(valueToCopy).then(function() {
            const $feedback = $('#copy-feedback');
            $feedback.fadeIn(200);
            setTimeout(function() {
                $feedback.fadeOut(500);
            }, 2000);
        }).catch(function(err) {
            console.error('Could not copy text: ', err);
        });
    },


    resetBrowserURL : function()
    {
        const pageURL = new URL(window.location);
        pageURL.search = "";
        window.history.pushState({}, '', pageURL);
    }
};


class GaugeChart {
    constructor(element, params) {
        this._element = element;
        this._initialValue = params.initialValue;
        this._higherValue = params.higherValue;
        this._title = params.title;
        this._subtitle = params.subtitle;
        this._customTicks = params.meterTicks;
    }

    _buildConfig() {
        let element = this._element;

        return {
            value: this._initialValue,
            valueIndicator: {
                color: '#fff'
            },
            geometry: {
                startAngle: 180,
                endAngle: 360
            },
            scale: {
                startValue: 0,
                endValue: this._higherValue,
                customTicks: this._customTicks,
                tick: {
                    length: 10
                },
                label: {
                    font: {
                        color: '#87959f',
                        size: 9,
                        family: '"Open Sans", sans-serif'
                    }
                }
            },
            title: {
                verticalAlignment: 'bottom',
                text: this._title,
                font: {
                    family: '"Open Sans", sans-serif',
                    color: '#00FFFF',
                    size: 10
                },

                subtitle: {
                    text: this._subtitle,
                    font: {
                        family: '"Open Sans", sans-serif',
                        color: '#fff',
                        weight: 700,
                        size: 28
                    }
                }
            },

            onInitialized: function () {
                let currentGauge = $(element);
                let circle = currentGauge.find('.dxg-spindle-hole').clone();
                let border = currentGauge.find('.dxg-spindle-border').clone();
                currentGauge.find('.dxg-title text').first().attr('y', 48);
                currentGauge.find('.dxg-title text').last().attr('y', 28);
                currentGauge.find('.dxg-value-indicator').append(border, circle);
            }
        };
    }

    init() {
        $(this._element).dxCircularGauge(this._buildConfig());
    }
};