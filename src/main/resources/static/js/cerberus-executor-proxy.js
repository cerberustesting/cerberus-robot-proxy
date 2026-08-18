/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
function loadProxyList() {
    getJSON("getProxyList").then(function (data) {
        data.forEach(function (session) {
            addSession(session, true);
        });
    });
}

function addSession(data, backfill) {
    Alpine.store('app').sessions.push({
        uuid: data.uuid,
        port: data.port,
        proxyType: data.proxyType,
        backfill: !!backfill
    });
    if (data.proxyType === "mitmproxy") {
        subscribeTraffic(data.uuid);
    }
}

function removeSession(uuid) {
    var store = Alpine.store('app');
    store.sessions = store.sessions.filter(function (session) {
        return session.uuid !== uuid;
    });
    if (store.tab === uuid) {
        store.tab = 'proxy';
    }
    if (window.harViewers) {
        delete window.harViewers[uuid];
    }
}

function startProxy() {
    var url = "startProxy?proxyType=" + el("proxyType").value;
    var port = el("proxyPort").value;
    if (port) {
        url += "&port=" + encodeURIComponent(port);
    }
    getJSON(url).then(function (data) {
        addSession(data);
        Alpine.store('app').tab = data.uuid;
    });
}

function stopProxy(uuid) {
    return getJSON("stopProxy?uuid=" + uuid).then(function () {
        unsubscribeTraffic(uuid);
        removeSession(uuid);
    });
}
