/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Document ready methods
 * @type type
 */
$(document).ready(function () {
    loadProxyList();
    getVideos();
    connect();
    getDoc("my-proxy-controller");
    getDoc("my-screen-recorder-controller");

});

/**
 * Get Doc from swagger
 * @param {type} api
 * @returns {undefined}
 */
function getDoc(api) {
    $.ajax({url: "./v2/api-docs",
        async: false,
        dataType: 'json',
        success: function (data) {
            console.log(data);
            Object.entries(data.paths).forEach(([key, value]) => {
                if (value.get !== undefined) {
                    if (value.get.tags.includes(api)) {
                        console.log(value);
                        $("#"+api).append('<li class="list-group-item">\n\
                        <a href="./swagger-ui.html#/'+api+'/'+value.get.operationId+'" class="badge badge-dark">/' + value.get.operationId.replace("UsingGET", "") + '</a>\n\
                        <span class="badge badge-primary">GET</span> : ' + value.get.summary + '</li>');
                    }
            } if (value.post !== undefined) {
                    if (value.post.tags.includes(api)) {
                        console.log(value);
                        $("#"+api).append('<li class="list-group-item">\n\
                        <a href="./swagger-ui.html#/'+api+'/'+value.post.operationId+'" class="badge badge-dark">/' + value.post.operationId.replace("UsingPOST", "") + '</a>\n\
                        <span class="badge badge-success">POST</span> : ' + value.post.summary + '</li>');
                    }
            }
            });
        }
    });
}

/**
 *  Method that shows a loader that was specified in a modal dialog
 * @param {type} uuid
 * @returns {undefined}
 */
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


//function executeCommand() {
////    var stompClient = null;
////    var commandToLaunch = $("#commandInput").val();
////    var socket = new SockJS('/getCommandLog');
////    stompClient = Stomp.over(socket);
////    stompClient.connect({}, function (frame) {
////        console.log('Connected: ' + frame);
////        stompClient.subscribe('/topic/commandLog', function (response) {
////            $("#commandLog").append("<tr><td>" + response.body + "</td></tr>");
////        });
////        
////        stompClient.send("/app/executeCommand2", {}, JSON.stringify({'command': commandToLaunch}));
////    });
//
//    var new_uri = "ws://localhost:8091/executeCommand2";
//    var socket = new WebSocket(new_uri);
//    socket.onopen = function (e) {
//    } //on "écoute" pour savoir si la connexion vers le serveur websocket s'est bien faite
//    socket.onmessage = function (e) {
//        $("#commandLog").append("<tr><td>" + e.body + "</td></tr>");
//    } //on récupère les messages provenant du serveur websocket
//    socket.onclose = function (e) {
//    } //on est informé lors de la fermeture de la connexion vers le serveur
//    socket.onerror = function (e) {
//    } //on traite les cas d'erreur*/
//
//}
