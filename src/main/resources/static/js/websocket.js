/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

var stompClient = null;

//function setConnected(connected) {
//    document.getElementById('connect').disabled = connected;
//    document.getElementById('disconnect').disabled = !connected;
//}

function connect() {
    var socket = new SockJS('/chat');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        //setConnected(true);
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/picture', function (messageOutput) {
            if (document.getElementById("list_entity_" + JSON.parse(messageOutput.body).uuid) == null) {
                drawContainer(JSON.parse(messageOutput.body).uuid);
                drawProgressBar(JSON.parse(messageOutput.body).uuid, 25, false);
                drawVideoLine(JSON.parse(messageOutput.body).uuid, null, null, null);
            }
            if (document.getElementById(JSON.parse(messageOutput.body).uuid) != null) {
                displayPicture(JSON.parse(messageOutput.body));
            }
            if (document.getElementById("numberOfScreenshot_" + JSON.parse(messageOutput.body).uuid) != null) {
                var nb = document.getElementById("numberOfScreenshot_" + JSON.parse(messageOutput.body).uuid);
                nb.innerHTML = JSON.parse(messageOutput.body).numberOfScreenshot;
            }

            if (JSON.parse(messageOutput.body).action === "stop") {
                $("#lastSessionVideo").empty();
                getVideo(JSON.parse(messageOutput.body).uuid);
            }

            if (JSON.parse(messageOutput.body).action === "startVideo") {
                getVideo(JSON.parse(messageOutput.body).uuid);
            }
            
            if (JSON.parse(messageOutput.body).action === "stopVideo") {
                getVideo(JSON.parse(messageOutput.body).uuid);
            }
            
            if (JSON.parse(messageOutput.body).action === "startSpeedIndex") {
                getVideo(JSON.parse(messageOutput.body).uuid);
            }
            
            if (JSON.parse(messageOutput.body).action === "stopSpeedIndex") {
               getVideo(JSON.parse(messageOutput.body).uuid);
            }

        });
    });
}

function disconnect() {
    if (stompClient != null) {
        stompClient.disconnect();
    }
    //setConnected(false);
    console.log("Disconnected");
}

function createPictureContainer(uuid) {
//    var response = document.getElementById('responsePicture');
//    var img = document.createElement('img');
//    img.id = messageOutput.uuid;
//    img.width = 640;
//    img.height = 480;
    $("#lastSessionVideo").empty();
    $("#lastSessionVideo").append('<div>\n\
<figcaption class="figure-caption">Executing...' + uuid + '\
<span onclick="stopRecording(\'' + uuid + '\')" class="badge badge-dark">/stopRecording</span>\n\
</figcaption>\n\
<figure class="figure">\n\
<img id=' + uuid + ' class="figure-img img-fluid rounded" width="640" height="480" >\n\
</figure></div>');

//    response.appendChild(img);
}


function displayPicture(messageOutput) {
    var response = document.getElementById(messageOutput.uuid);
    response.setAttribute("src", "./image?path=" + messageOutput.text);

}

function createNewLine(uuid) {

    $("#conversationDiv").prepend('<li class="list-group-item" id="list_entity_' + uuid + '"></li>');
    $("#list_entity_" + uuid).append('<div class="progress"><div class="progress-bar bg-danger" onclick="createPictureContainer(\'' + uuid + '\')" role="progressbar" style="width: 25%;" aria-valuenow="25" aria-valuemin="0" aria-valuemax="100">' + uuid.split("-")[0] + '</div></div>');
    $("#list_entity_" + uuid).append('<div class="d-inline-flex col-lg-12">\n\
        <div class="col-lg-3"><ion-icon name="images-outline"></ion-icon> : <div id="numberOfScreenshot_' + uuid + '"></div> </div>\n\
        <div class="col-lg-3"><ion-icon name="reload-circle-outline"></ion-icon></div></div>');

}

//function loadCurrentSession(uuid) {
//    $("#lastSessionVideo").empty();
//    $("#lastSessionVideo").append('<div>\n\
//      <figure class="figure">\n\
//      <video class="figure-img img-fluid rounded" width="640" height="480" controls autoplay><source src="./streamVideo?path=' + id + '" type="video/mp4"/></video>\n\
//      <figcaption class="figure-caption">Execution : ' + id + '</figcaption>\n\
//      </figure></div>');
//}