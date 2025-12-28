window.addEventListener("load", (wevt) => {
  const websocket = new WebSocket(`ws://${location.host || '127.0.0.1:8080'}/view/updates`);

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
    }
  });

  websocket.addEventListener("error", (socketEvent) => {
    console.error(socketEvent);
  });

  websocket.addEventListener("close", (socketEvent) => {});
});
