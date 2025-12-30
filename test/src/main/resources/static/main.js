class DataTable extends HTMLElement {
    #ROW_TEMPLATE;
    #tbody;
    #entries = [];

    constructor() {
        super();
        this.#ROW_TEMPLATE = this.shadowRoot.host.querySelector('template#row-template');
        this.removeChild(this.#ROW_TEMPLATE);
        this.#tbody = this.shadowRoot.querySelector('tbody');
        console.log(this.#ROW_TEMPLATE, this.#tbody);
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

window.addEventListener("load", (wevt) => {
    /** @type {DataTable} dataTable **/
    const dataTable = document.getElementById('clustersNodes');
    const lastSyncNode = document.getElementById('lastClusterSyncDate');
    const resyncButton = document.getElementById('resyncButton');
    const websocket = new WebSocket(`ws://${location.host || '127.0.0.1:8080'}/view/updates`);

    resyncButton.addEventListener("click", (evt) => {
        if (websocket.readyState !== WebSocket.OPEN) {
            alert('Connection with server is not established. Try again later.');

            return;
        }

        websocket.send(JSON.stringify({
            type: "query_cluster_details",
            requestedAt: new Date().toISOString()
        }));
    });

    websocket.addEventListener("open", (socketEvent) => {
        console.log("Synchronization is enabled", socketEvent);

        websocket.send(JSON.stringify({
            type: "query_cluster_details",
            requestedAt: new Date().toISOString()
        }));
    });

    websocket.addEventListener("message", (socketEvent) => {
    console.log("Received message", socketEvent);

    const incomingEvent = JSON.parse(socketEvent.data);

    switch (incomingEvent.type) {
      case "cluster_details":
          lastSyncNode.textContent = incomingEvent.processedAt;

          console.log(incomingEvent, "time to handle it");
          const entries = [];

          for (const cluster of incomingEvent.data.clusters) {
             for (const node of cluster.nodes) {
                 entries.push({
                     name: cluster.name,
                     address: `${node.hostname}`,
                     tasks: ``,
                     last_heartbeat: node.lastHeartbeat,
                     trip_time: node.lastTrip
                 });
             }
          }

          dataTable.setEntries(entries);
    }
    });

    websocket.addEventListener("error", (socketEvent) => {
    console.error(socketEvent);
    });

    websocket.addEventListener("close", (socketEvent) => {});
});
