const applyGlobalStyles = (container) => {
    const style = document.createElement('link');
    style.rel = 'stylesheet';
    style.type = 'text/css';
    style.href = 'main.css';

    container.appendChild(style);
}

class DataTable extends HTMLElement {
    #ROW_TEMPLATE;
    #tbody;
    #entries = [];

    constructor() {
        super();
        this.#ROW_TEMPLATE = this.shadowRoot.host.querySelector('template#row-template');
        this.removeChild(this.#ROW_TEMPLATE);
        this.#tbody = this.shadowRoot.querySelector('tbody');
    }

    addEntry(item) {
        this.#entries.push(item);
        this.#renderAll();
    }

    setEntries(items) {
        this.#entries = [ ...items ];
        this.#renderAll();
    }

    #renderAll() {
        this.#tbody.replaceChildren();
        this.#entries.forEach(item => this.#renderRow(item));
    }

    #renderRow(item) {
        const clone = this.#ROW_TEMPLATE.content.cloneNode(true);
        const nodeWalker = document.createTreeWalker(clone, NodeFilter.SHOW_COMMENT, null);

        let nextNode = nodeWalker.nextNode();
        while (nextNode) {
            const nextComment = nextNode;
            const commentValue = nextComment.nodeValue;

            if (!commentValue.startsWith('@') || !commentValue.endsWith('@')) {
                continue;
            }

            const key = commentValue.slice(1, -1).trim();
            const textNode = document.createTextNode(item[key] || "");

            nextNode = nodeWalker.nextNode();

            nextComment.parentNode.appendChild(textNode);
            nextComment.parentNode.removeChild(nextComment);
        }

        this.#tbody.appendChild(clone);
    }
}

customElements.define('data-table', DataTable);

class LogList extends HTMLElement {
    #LOG_ITEM_TEMPLATE;
    #listRef;
    #entries = [];

    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        applyGlobalStyles(this.shadowRoot);
        this.#listRef = this.shadowRoot.ownerDocument.createElement('ul');
        this.#listRef.classList.add('log-list__container');
        this.shadowRoot.appendChild(this.#listRef);
        this.#LOG_ITEM_TEMPLATE = this.shadowRoot.host.querySelector('template#log-template');
        this.removeChild(this.#LOG_ITEM_TEMPLATE);
    }

    addEntry(logEntry) {
        this.#entries.push(logEntry);
        this.#renderAll();
    }

    setEntries(items) {
        this.#entries = [ ...items ];
        this.#renderAll();
    }

    #renderAll() {
        this.#listRef.replaceChildren();
        this.#entries.forEach(entry => this.#renderLogEntry(entry));
    }

    #renderLogEntry(item) {
        const clone = this.#LOG_ITEM_TEMPLATE.content.cloneNode(true);
        const nodeWalker = document.createTreeWalker(clone, NodeFilter.SHOW_COMMENT, null);

        let nextNode = nodeWalker.nextNode();
        while (nextNode) {
            const nextComment = nextNode;
            const commentValue = nextComment.nodeValue;

            if (!commentValue.startsWith('@') || !commentValue.endsWith('@')) {
                continue;
            }

            const key = commentValue.slice(1, -1).trim();
            const textNode = document.createTextNode(item[key] || "");

            nextNode = nodeWalker.nextNode();

            nextComment.parentNode.appendChild(textNode);
            nextComment.parentNode.removeChild(nextComment);
        }

        this.#listRef.appendChild(clone);
    }
}

customElements.define('log-list', LogList);

class EmittedEvent extends CustomEvent {
    #originalEvent;
    #currentTarget;
    #target;

    /** @returns {Event} */
    get originalEvent() {
        return this.#originalEvent;
    }

    get currentTarget() {
        return this.#currentTarget;
    }

    get target() {
        return this.#target;
    }

    constructor(type, originalEvent, detail) {
        super(type, { detail, bubbles: false, composed: true });

        this.#originalEvent = originalEvent;
        this.#currentTarget = originalEvent.currentTarget;
        this.#target = originalEvent.target;
    }
}

const eventBus = new EventTarget();
window.$emit = (eventName, event, data) => {
    console.log(this, event, event.currentTarget, event.target);
    console.log(eventName, data);

    eventBus.dispatchEvent(new EmittedEvent(eventName, event, data));
};

const clusterDetailsEntries = [];

class CommandExecutionDialogController {
    /** @type {HTMLDialogElement} **/
    #dialog;

    /** @type {HTMLFormElement} **/
    #dialogForm;

    #executionStatusCode;
    #executionResult;

    constructor(webservice) {
        this.#dialog = document.getElementById('commandExecutionDialog');
        this.#dialogForm = this.#dialog.querySelector('form');
        this.#executionStatusCode = this.#dialog.querySelector('#execution__statusCode');
        this.#executionResult = this.#dialog.querySelector('#execution__output');

        this.#dialogForm.addEventListener('submit', (evt) => {
            evt.preventDefault();
            const formData = new FormData(evt.currentTarget);

            webservice.executeCommand(
                formData.get('targetHostname'),
                formData.get('targetPort'),
                formData.get('runtimeName'),
                {
                    command: formData.get('command'),
                    positionalArguments: [],
                    options: {}
                },
                {
                    repeatTimes: formData.get('howManyTimesToRepeat')
                }
            );
        });
        this.#dialog.querySelector('.dialog__closeBtn')
            .addEventListener('click', () => this.close())
        ;
    }

    updateOutput(statusCode, output) {
        if (!this.#dialog.open) {
            return;
        }

        this.#executionStatusCode.textContent = statusCode;
        this.#executionResult.textContent = output;
    }

    showFor(node) {
        this.#dialogForm.elements.namedItem('targetHostname').value = node.hostname;
        this.#dialogForm.elements.namedItem('targetPort').value = node.communicationPort;

        const runtimeSelect = this.#dialogForm.elements.namedItem('runtimeName');
        runtimeSelect.replaceChildren();
        for (const runtime of node.supportedRuntimes) {
            const option = document.createElement('option');
            option.value = runtime;
            option.textContent = runtime;
            runtimeSelect.appendChild(option);
        }

        this.#dialog.showModal();
    }

    close() {
        this.#dialogForm.reset();
        this.#executionStatusCode.textContent = '';
        this.#executionResult.textContent = '';
        this.#dialog.close();
    }
}

const connectToTheWebSync = (messageHandler, address) => {
    let websocketHandle

    const connect = () => {
        address = address || location.host || '127.0.0.1:8080';
        const websocket = new WebSocket(`ws://${address}/view/updates`);

        websocket.addEventListener("open", (socketEvent) => {
            console.log("Synchronization is enabled", socketEvent);

            websocket.send(JSON.stringify({
                type: "query_cluster_details",
                requestedAt: new Date().toISOString()
            }));

            scheduleNextUpdate();
        });

        websocket.addEventListener("message", (socketEvent) => messageHandler(socketEvent, websocket, address));

        websocket.addEventListener("error", (socketEvent) => {
            console.error(socketEvent);
        });

        websocket.addEventListener("close", (socketEvent) => {
            websocketHandle = connect();
        });

        return websocket;
    }

    const scheduleNextUpdate = () => {
        setTimeout(() => {
            if (websocketHandle.readyState === WebSocket.OPEN) {
                console.log("Sending request for cluster status...");
                websocketHandle.send(JSON.stringify({
                    type: "query_cluster_details",
                    requestedAt: new Date().toISOString()
                }));

                scheduleNextUpdate();
            }
        }, 5000);
    }

    websocketHandle = connect();

    const queryClusters = () => {
        websocketHandle.send(JSON.stringify({
            type: "query_cluster_details",
            requestedAt: new Date().toISOString()
        }));
    }

    const executeCommand = (targetHostname, targetPort, runtimeName, input, additionalOptions) => {
        websocketHandle.send(JSON.stringify({
            type: "execute_command",
            targetHostname,
            targetPort,
            repeatTimes: additionalOptions.repeatTimes || 0,
            requestedAt: new Date().toISOString(),
            runtimeName,
            input
        }));
    }

    return {
        get isConnected() {
            return websocketHandle.readyState === WebSocket.OPEN;
        },
        queryClusters,
        executeCommand
    };
}

const createNodeLogTab = (webTargetAddress) => {
    const nodeLogsTab = document.createElement('div');
    nodeLogsTab.setAttribute('data-address', webTargetAddress);
    nodeLogsTab.classList.add('tab-bar__item');
    nodeLogsTab.appendChild(document.createTextNode(`Node ${webTargetAddress}`));

    return nodeLogsTab;
}

window.addEventListener("load", (wevt) => {
    const currentBaseUrl = location.host;
    const logsPerNode = new Map();
    let connectionPool = new Map;
    let filteredLogs = [];
    let selectedNode = currentBaseUrl;
    /** @type {DataTable} dataTable **/
    const dataTable = document.getElementById('clustersNodes');
    /** @type {LogList} currentLogList */
    const currentLogList = document.getElementById('currentNodeLogs');
    const lastSyncNode = document.getElementById('lastClusterSyncDate');
    const resyncButton = document.getElementById('resyncButton');
    const nodesLogsSwitchTab = document.getElementById('detectedNodes');
    nodesLogsSwitchTab.addEventListener('click', e => {
        /** @var {HTMLElement} targetNode **/
        const targetNode = e.target;
        const targetAddress = targetNode.getAttribute('data-address');
        selectedNode = targetAddress;

        for (const childNode of nodesLogsSwitchTab.childNodes) {
            if (childNode instanceof HTMLElement && childNode.classList.contains('selected')) {
                childNode.classList.remove('selected');
                break;
            }
        }

        targetNode.classList.add('selected');

        filteredLogs = [...(logsPerNode.get(targetAddress) || [])];
        currentLogList.setEntries(filteredLogs);
        console.log(Array.from(connectionPool.entries()), logsPerNode.get(targetAddress));
    });
    const logsSearchBar = document.getElementById('searchLogsField');
    logsSearchBar.addEventListener('input', (ev) => {
        filteredLogs =  [...(logsPerNode.get(selectedNode) || [])].filter(it => it.data.includes(ev.currentTarget.value));
        currentLogList.setEntries(filteredLogs);
    });

    const standardHandler = (socketEvent, websocket, sourceAddress) => {
        console.log("Received message", socketEvent);

        const incomingEvent = JSON.parse(socketEvent.data);

        switch (incomingEvent.type) {
            case "log_message":
                if (!logsPerNode.has(sourceAddress)) {
                    logsPerNode.set(sourceAddress, []);
                }

                const nodeLogs = logsPerNode.get(sourceAddress);
                nodeLogs.push(incomingEvent);

                if (sourceAddress !== selectedNode) {
                    break;
                }
                console.log('received log message', incomingEvent);
                filteredLogs = [...nodeLogs];
                currentLogList.addEntry(incomingEvent);
                break;
            case "cluster_details":
                lastSyncNode.textContent = incomingEvent.processedAt;
                clusterDetailsEntries.splice(0, clusterDetailsEntries.length);

                const entries = [];
                for (const cluster of incomingEvent.data.clusters) {
                    for (const node of cluster.nodes) {
                        const nodeAddress = `${node.hostname}:${node.communicationPort}`;
                        const webTargetAddress = `${node.hostname}:${node.webPort}`;

                        entries.push({
                            name: cluster.name,
                            address: nodeAddress,
                            tasks: ``,
                            last_heartbeat: node.lastHeartbeat,
                            trip_time: node.lastTrip,
                            supportedRuntimes: node.supportedRuntimes.join(', ')
                        });

                        clusterDetailsEntries.push({
                            ...node,
                            clusterName: cluster.name
                        });

                        if (!connectionPool.has(webTargetAddress)) {
                            console.log(webTargetAddress, 'fff');
                            logsPerNode.set(webTargetAddress, []);
                            connectionPool.set(webTargetAddress, connectToTheWebSync(standardHandler, webTargetAddress));
                            const nodeLogsTab = createNodeLogTab(webTargetAddress);

                            if (nodesLogsSwitchTab.children.length === 0) {
                                nodeLogsTab.classList.add('selected');
                            }
                            nodesLogsSwitchTab.appendChild(nodeLogsTab);
                        }
                    }
                }

                dataTable.setEntries(entries);
                break;
            case "execution_result":
                commandExecutionDialogController.updateOutput(incomingEvent.data.result.statusCode, incomingEvent.data.result.output);
        }
    };
    const webService = connectToTheWebSync(standardHandler, currentBaseUrl);
    connectionPool.set(currentBaseUrl, webService);
    const nodeLogsTab = createNodeLogTab(currentBaseUrl);
    nodeLogsTab.classList.add('selected');
    nodesLogsSwitchTab.appendChild(nodeLogsTab);
    const commandExecutionDialogController = new CommandExecutionDialogController(webService);

    eventBus.addEventListener('executeOnNode', (evt) => {
        if (!webService.isConnected) {
            alert('Connection with server is not established. Try again later.');

            return;
        }

        const target = evt.currentTarget;
        const index = target.closest('tr').sectionRowIndex;
        const entryData = clusterDetailsEntries[index];

        commandExecutionDialogController.showFor(entryData);
    });

    resyncButton.addEventListener("click", (evt) => {
        if (!webService.isConnected) {
            alert('Connection with server is not established. Try again later.');

            return;
        }

        webService.queryClusters();
    });
});
