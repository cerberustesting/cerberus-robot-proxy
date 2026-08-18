/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Small fetch/DOM helpers used across the front-end scripts.
 */
function getJSON(url, options) {
    return fetch(url, options).then(function (response) {
        if (!response.ok) {
            throw new Error("Request failed: " + url + " (" + response.status + ")");
        }
        return response.json();
    });
}

function el(id) {
    return document.getElementById(id);
}

function appendHtml(target, html) {
    if (target) {
        target.insertAdjacentHTML('beforeend', html);
    }
}

/**
 * Get Doc from swagger
 * @param {type} api
 * @returns {undefined}
 */
function getDoc(api) {
    getJSON("./v2/api-docs").then(function (data) {
        console.log(data);
        var badgeDark = "inline-flex items-center bg-gray-800 hover:bg-gray-700 text-white text-xs font-medium px-2 py-0.5 rounded";
        var badgeGet = "inline-flex items-center bg-blue-600 text-white text-xs font-medium px-2 py-0.5 rounded";
        var badgePost = "inline-flex items-center bg-green-600 text-white text-xs font-medium px-2 py-0.5 rounded";
        var target = el(api);
        Object.entries(data.paths).forEach(([key, value]) => {
            if (value.get !== undefined) {
                if (value.get.tags.includes(api)) {
                    console.log(value);
                    appendHtml(target, '<li class="p-2 text-sm flex items-center gap-2 flex-wrap">\n\
                    <a href="./swagger-ui.html#/'+api+'/'+value.get.operationId+'" class="' + badgeDark + '">/' + value.get.operationId.replace("UsingGET", "") + '</a>\n\
                    <span class="' + badgeGet + '">GET</span> : ' + value.get.summary + '</li>');
                }
        } if (value.post !== undefined) {
                if (value.post.tags.includes(api)) {
                    console.log(value);
                    appendHtml(target, '<li class="p-2 text-sm flex items-center gap-2 flex-wrap">\n\
                    <a href="./swagger-ui.html#/'+api+'/'+value.post.operationId+'" class="' + badgeDark + '">/' + value.post.operationId.replace("UsingPOST", "") + '</a>\n\
                    <span class="' + badgePost + '">POST</span> : ' + value.post.summary + '</li>');
                }
        }
        });
    });
}
