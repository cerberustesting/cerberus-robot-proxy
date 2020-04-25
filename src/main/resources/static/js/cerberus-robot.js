/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
$(document).ready(function () {
        loadProxyList();
    });


function getSpeedIndex() {
    var url = $("#speedIndexUrl").val();
    $.ajax({url: "launch?url="+url,
        async: false,
        dataType: 'json',
        success: function (data) {
            //console.log(data);
            $("#speedIndexResult").text(JSON.stringify(data, null, '\t'));
            PR.prettyPrint();
        }
    });
}


function loadProxyList() {
    $.ajax({url: "getProxyList",
        async: false,
        dataType: 'json',
        success: function (data) {
            for (var i in data)
            {
                generateLogDiv(data[i]);
            }
        }
    });
}

function generateLogDiv(data) {

    $("#myProxy").append('<div class="col-md-6" id="container_'+data.uuid+'"><div class="card-header"><h6 class="my-0 font-weight-normal">Proxy '
    +data.uuid+' on port '+data.port+'<ion-icon class="float-right" name="cloud-download" onclick="getHar(\'' + data.uuid + '\')"></ion-icon>\n\
    <ion-icon class="float-right" name="square" onclick="stopProxy(\'' + data.uuid + '\')"></ion-icon>\n\
    <div id="loader_'+data.uuid+'"></div></h6></div><div><pre id="' + data.uuid + '"></pre></div></div>');
    $("#" + data.uuid).attr("class", "prettyprint");
    $("#" + data.uuid).attr("style", "max-height:300px");
    $("#" + data.uuid).append("\n");
    $("#" + data.uuid).append(new Date($.now()));
    $("#" + data.uuid).append("   *** Proxy ");
    $("#" + data.uuid).append(data.uuid);
    $("#" + data.uuid).append(" started ***   ");
    $("#" + data.uuid).removeClass('prettyprinted');
    PR.prettyPrint();
}



function startProxy() {
    $.ajax({url: "startProxy",
        async: false,
        dataType: 'json',
        success: function (data) {
            generateLogDiv(data);
        }
    });
}

function getHar(uuid) {
    $.ajax({url: "getHar?uuid=" + uuid + "&emptyResponseContentText=true",
        async: false,
        dataType: 'json',
        success: function (data) {
            $("#" + uuid).append("\n");
            $("#" + uuid).append(new Date($.now()));
            $("#" + uuid).append("   *** Get Har for Proxy ");
            $("#" + uuid).append(uuid);
            $("#" + uuid).append(" ***");
            $("#" + uuid).append("\n");
            $("#" + uuid).append(JSON.stringify(data, null, '\t'));
            $("#" + uuid).append("\n");
            $("#" + uuid).removeClass('prettyprinted');
            PR.prettyPrint();
        }
    });
}


function stopProxy(uuid) {
    showLoader(uuid);
    $.ajax({url: "stopProxy?uuid=" + uuid,
        async: false,
        dataType: 'json',
        success: function (data) {
            console.log("success");
            $("#container_" + uuid).remove();
        }
    });
}

function showLoader(uuid) {
    $("#loader_" + uuid).append('<div class="loader" id="loader"></div>');
}

/**
 * Method that hides a loader that was specified in a modal dialog
 * @param {type} element
 */
function hideLoader(uuid) {
    $("#loader_" + uuid).empty;
}


function startSelenium() {
//    $.ajax({url: "startSelenium",
//        async: false,
//        dataType: 'json',
//        success: function (data) {
//            console.log(data);
//            connect();
//            //$("#seleniumLog").text(JSON.stringify(data,null,'\t'));
//            //PR.prettyPrint();
//        }
//    });
    connect();
}





function connect() {
    var stompClient = null;
    var socket = new SockJS('/getSeleniumLog');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/greetings', function (greeting) {
            showGreeting(greeting.body);
        });
        stompClient.send("/app/startSelenium", {}, JSON.stringify({'name': "toto"}));
    });
}

function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    console.log("Disconnected");
}


function showGreeting(message) {
    $("#seleniumLog").append("<tr><td>" + message + "</td></tr>");
}

function executeCommand() {
//    var stompClient = null;
//    var commandToLaunch = $("#commandInput").val();
//    var socket = new SockJS('/getCommandLog');
//    stompClient = Stomp.over(socket);
//    stompClient.connect({}, function (frame) {
//        console.log('Connected: ' + frame);
//        stompClient.subscribe('/topic/commandLog', function (response) {
//            $("#commandLog").append("<tr><td>" + response.body + "</td></tr>");
//        });
//        
//        stompClient.send("/app/executeCommand2", {}, JSON.stringify({'command': commandToLaunch}));
//    });

    var new_uri = "ws://localhost:8091/executeCommand2";

    var socket = new WebSocket(new_uri);

    socket.onopen = function (e) {
    } //on "écoute" pour savoir si la connexion vers le serveur websocket s'est bien faite
    socket.onmessage = function (e) {
        $("#commandLog").append("<tr><td>" + e.body + "</td></tr>");
    } //on récupère les messages provenant du serveur websocket
    socket.onclose = function (e) {
    } //on est informé lors de la fermeture de la connexion vers le serveur
    socket.onerror = function (e) {
    } //on traite les cas d'erreur*/

}
