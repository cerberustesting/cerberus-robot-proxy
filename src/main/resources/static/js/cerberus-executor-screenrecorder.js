/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
function getVideos() {
    $.ajax({url: "videos",
        async: false,
        dataType: 'json',
        success: function (data) {
            for (var i in data)
            {
                drawCompleteLine(data[i]);
            }

        }


    });
}

function getVideo(uuid) {
    $.ajax({url: "video",
        async: false,
        dataType: 'json',
        data: {uuid: uuid},
        success: function (data) {
            drawCompleteLine(data);
        }


    });
}

function drawCompleteLine(data) {
    $("#list_entity_" + data.uuid).remove();
    drawContainer(data.uuid);
    drawProgressBar(data.uuid, getProgressBarPercentage(data.vncSession, data.myVideo, data.speedIndex), getProgressStatus(data.vncSession, data.myVideo, data.speedIndex));
    drawVideoLine(data.uuid, data.vncSession, data.myVideo, data.speedIndex);
}

function drawContainer(uuid) {
    //Draw container
    $("#conversationDiv").prepend('<li class="list-group-item" id="list_entity_' + uuid + '"></li>');
}

function drawProgressBar(uuid, progress, paused) {
    //Draw progress bar
    if (100 === progress) {
        $("#list_entity_" + uuid).append('<div id="progress_' + uuid + '" class="progress"><div class="progress-bar bg-success" role="progressbar" style="width: 100%;" aria-valuenow="100" aria-valuemin="0" aria-valuemax="100">' + uuid.split("-")[0] + '</div></div>');
    } else {
        if (paused) {
            $("#list_entity_" + uuid).append('<div id="progress_' + uuid + '" class="progress"><div class="progress-bar bg-warning" role="progressbar" style="width: ' + progress + '%;" aria-valuenow="' + progress + '" aria-valuemin="0" aria-valuemax="100">' + uuid.split("-")[0] + '</div></div>');
        } else {
            $("#list_entity_" + uuid).append('<div id="progress_' + uuid + '" class="progress"><div class="progress-bar progress-bar-striped progress-bar-animated" role="progressbar" style="width: ' + progress + '%;" aria-valuenow="' + progress + '" aria-valuemin="0" aria-valuemax="100">' + uuid.split("-")[0] + '</div></div>');
        }
    }
}

function getProgressBarPercentage(vncSession, myVideo, speedIndex) {
    var percentage = 0;
    percentage += vncSession.status === 'started' ? 25 : 0;
    percentage += vncSession.status === 'completed' ? 50 : 0;
    percentage += myVideo !== null && myVideo.status === 'started' ? 15 : 0;
    percentage += myVideo !== null && myVideo.status === 'completed' ? 25 : 0;
    percentage += speedIndex !== null && speedIndex.status === 'started' ? 15 : 0;
    percentage += speedIndex !== null && speedIndex.status === 'completed' ? 25 : 0;
    return percentage;
}

function getProgressStatus(vncSession, myVideo, speedIndex) {
    var paused = false;
    paused = vncSession.status === 'completed';
    if (myVideo !== null) {
        paused = paused && myVideo.status === 'completed';
    }
    if (speedIndex !== null) {
        paused = paused && speedIndex.status === 'completed';
    }
    return paused;
}

function drawVideoLine(uuid, vncSession, myVideo, speedIndex) {
//Draw content
    var nss = '';
    if (vncSession != null) {
        nss = '<div class="col-lg-3 row"><ion-icon name="images-outline" onclick="createPictureContainer(\'' + uuid + '\')"></ion-icon><div id="numberOfScreenshot_' + uuid + '">  : ' + vncSession.myScreenshot.length + '</div> </div>';
    } else {
        nss = '<div class="col-lg-3 row"><ion-icon name="images-outline" onclick="createPictureContainer(\'' + uuid + '\')"></ion-icon><div id="numberOfScreenshot_' + uuid + '"></div> </div>';
    }
    var nbs = '';
    if (speedIndex != null) {
        nbs = '<div class="col-lg-3"><ion-icon name="calculator-outline"></ion-icon> : ' + speedIndex.numberOfStep + ' </div>';
    }
    var vdp = '';
    if (myVideo != null) {
        vdp = '<div class="col-lg-3"><ion-icon name="film-outline" alt="show video"  style="cursor: pointer;" onclick="loadLastVideo(\'' + myVideo.path + '\')"></ion-icon> : ' + myVideo.duration + 's </div>';
    } else {
        vdp = '<div class="col-lg-3"><ion-icon name="videocam-outline" alt="generate video" style="cursor: pointer;"  onclick="generateVideo(\'' + uuid + '\')"></ion-icon></div>';
    }
    var spi = '';
    if (speedIndex != null) {
        spi = '<div class="col-lg-3"><ion-icon name="stats-chart-outline" alt="show speedIndex"  style="cursor: pointer;"  onclick="getSpeedIndex(\'' + uuid + '\')"></ion-icon></div>';
    } else {
        spi = '<div class="col-lg-3"><ion-icon name="send-outline" alt="calculate speedIndex"  style="cursor: pointer;"  onclick="calculateSpeedIndex(\'' + uuid + '\')"></ion-icon></div>';
    }

    $("#list_entity_" + uuid).append('<div class="d-inline-flex col-lg-12">' + nss + vdp + nbs + spi + '</div>');
}

function drawChart(data) {

    $("#lastSessionVideo").empty();
    $("#lastSessionVideo").append('<canvas id="myChart"></canvas>');
    var default_colors = ['#3366CC', '#DC3912', '#FF9900', '#109618', '#990099', '#3B3EAC', '#0099C6', '#DD4477', '#66AA00', '#B82E2E', '#316395', '#994499', '#22AA99', '#AAAA11', '#6633CC', '#E67300', '#8B0707', '#329262', '#5574A6', '#3B3EAC'];

    var ctx = document.getElementById('myChart').getContext('2d');

    console.log(data);

    var ds = [];
    for (const siStep of data.speedIndexStep) {

        console.log(siStep);
        var dt = [];

        for (const siSample of siStep.speedIndexSample) {
            var d = {
                t: new Date(siSample.myScreenshot.timestamp),
                y: siSample.diffPercentage
            }
            dt.push(d);
        }

        var serie = {
            borderColor: default_colors[ds.length],
            label: siStep.message,
            data: dt
        };

        ds.push(serie);

    }

    console.log(ds);

    var chart = new Chart(ctx, {
        type: 'line',

        data: {datasets: ds},
        options: {
            scales: {
                xAxes: [{
                        type: 'time',
                        time: {
                            unit: 'millisecond'
                        }
                    }]
            }
        }
    });
}

function getSpeedIndex(uuid) {
    $.ajax({url: "speedIndex?uuid=" + uuid,
        async: false,
        dataType: 'json',
        success: function (data) {
            drawChart(data.speedIndex);
        }
    });
}


function loadLastVideo(id) {
    $("#lastSessionVideo").empty();
    $("#lastSessionVideo").append('<div>\n\
      <figure class="figure">\n\
      <video class="figure-img img-fluid rounded" width="640" height="480" controls autoplay><source src="./streamVideo?path=' + id + '" type="video/mp4"/></video>\n\
      <figcaption class="figure-caption">Execution : ' + id + '</figcaption>\n\
      </figure></div>');
}



function startRecording() {

    var vncHost = $("#vncHost").val();
    var vncPort = $("#vncPort").val();
    var vncPassword = $("#vncPassword").val();
    $.ajax({url: "startRecording",
        method: "post",
        async: false,
        data: {vncHost: vncHost, vncPort: vncPort, vncPassword: vncPassword},
        dataType: 'json',
        success: function (data) {
            //started
            console.log("started");
        }
    });
}

function stopRecording(uuid) {

    $.ajax({url: "stopRecording",
        method: "post",
        async: false,
        data: {uuid: uuid},
        dataType: 'json',
        success: function (data) {
            //started
            console.log("stopped");
        }
    });
}


function generateVideo(uuid) {
    $("#progress_" + uuid).children().attr("class", "progress-bar progress-bar-striped progress-bar-animated");
    $.ajax({url: "generateVideo",
        method: "post",
        async: true,
        data: {uuid: uuid},
        dataType: 'json',
        success: function (data) {
            //started
            console.log("videoGenerated");
        }
    });
}

function calculateSpeedIndex(uuid) {
    $("#progress_" + uuid).children().attr("class", "progress-bar progress-bar-striped progress-bar-animated");
    $.ajax({url: "calculateSpeedIndex",
        method: "post",
        async: true,
        data: {uuid: uuid},
        dataType: 'json',
        success: function (data) {
            //started
            console.log("speedIndexCalculated");
        }
    });
}