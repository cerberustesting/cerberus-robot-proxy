/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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

    $("#myProxy").append('<div class="col-md-6" id="container_' + data.uuid + '"><div class="card-header"><h6 class="my-0 font-weight-normal">Proxy '
            + data.uuid + ' started on port ' + data.port + '<br/>\n\
    <span onclick="getHar(\'' + data.uuid + '\')" class="badge badge-dark">/getHar</span>  \n\
    <span onclick="getStats(\'' + data.uuid + '\')" class="badge badge-dark">/getStats</span>  \n\
    <span onclick="clearHar(\'' + data.uuid + '\')" class="badge badge-dark">/clearHar</span>  \n\
    <span onclick="stopProxy(\'' + data.uuid + '\')" class="badge badge-dark">/stopProxy</span>\n\
    <div id="loader_' + data.uuid + '"></div></h6></div><div><pre id="' + data.uuid + '"></pre></div></div>');
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

function getStats(uuid) {
    $.ajax({url: "getStats?uuid=" + uuid,
        async: false,
        dataType: 'json',
        success: function (data) {
            $("#" + uuid).append("\n");
            $("#" + uuid).append(new Date($.now()));
            $("#" + uuid).append("   *** Get Stats for Proxy ");
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

function clearHar(uuid) {
    $.ajax({url: "clearHar?uuid=" + uuid,
        async: false,
        dataType: 'json',
        success: function (data) {
            $("#" + uuid).append("\n");
            $("#" + uuid).append(new Date($.now()));
            $("#" + uuid).append("   *** Clear HAR for Proxy ");
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