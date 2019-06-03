/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
function loadPerformance() {
    $.ajax({url: "getSystemInfo",
        async: false,
        dataType: 'json',
        success: function (data) {
            console.log(data);
            $("#performance").text(JSON.stringify(data, null, '\t'));
            PR.prettyPrint();
        }
    });
}


function loadProxyList() {
    $.ajax({url: "getProxyList",
        async: false,
        dataType: 'json',
        success: function (data) {
            console.log(data);
            $("#proxy").text(JSON.stringify(data, null, '\t'));
            PR.prettyPrint();
        }
    });
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
