/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

var stompClient = null;
var trafficSubscriptions = {};

function connect() {
    var socket = new SockJS('/chat');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
    });
}

function subscribeTraffic(uuid, retries) {
    if (trafficSubscriptions[uuid]) {
        return;
    }

    if (!stompClient || !stompClient.connected) {
        retries = (retries || 0) + 1;
        if (retries > 25) {
            console.log("Unable to subscribe to live traffic for " + uuid + ": websocket not connected");
            return;
        }
        setTimeout(function () {
            subscribeTraffic(uuid, retries);
        }, 200);
        return;
    }

    trafficSubscriptions[uuid] = stompClient.subscribe('/topic/traffic/' + uuid, function (messageOutput) {
        var entry;
        try {
            entry = JSON.parse(messageOutput.body);
        } catch (e) {
            console.log("Invalid traffic entry received for " + uuid, e);
            return;
        }
        if (window.harViewers && window.harViewers[uuid]) {
            window.harViewers[uuid].addEntry(entry);
        }
    });
}

function unsubscribeTraffic(uuid) {
    if (trafficSubscriptions[uuid]) {
        trafficSubscriptions[uuid].unsubscribe();
        delete trafficSubscriptions[uuid];
    }
}

function disconnect() {
    if (stompClient != null) {
        stompClient.disconnect();
    }
    console.log("Disconnected");
}