$(document).ready(function () {
    $("#notBeforeDate").val(new Date().toISOString().split('T')[0]);
    $("#notAfterDate").val(new Date(new Date().getTime()+(730*24*60*60*1000)).toISOString().split('T')[0]);
    $('#generateCertsButton').click(function()
        {
            generateCerts();
        }
    );
});

function generateCerts() {
    //Avoid to call when the password is empty
    if ($("#password").val() == "") {
        return false;
    }
    $.ajax
    ({
        url: "certs/generate",
        async: true,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        data: JSON.stringify({
            commonName: encodeURIComponent($("#commonName").val()),
            organization: encodeURIComponent($("#organization").val()),
            notBeforeDate: encodeURIComponent(new Date($('#notBeforeDate').val()).toISOString()),
            notAfterDate: encodeURIComponent(new Date($('#notAfterDate').val()).toISOString()),
            password: encodeURIComponent($('#password').val())
        }),
        beforeSend: function() {
            console.info("Request for generating certificates sent to the server.");
            hideErrorMessage();
            showLoadingMessage();
        },
        error: function(jqXHR, textStatus, errorThrown) {
            console.error("An error occurred during generation of certificates " + errorThrown);
            hideLoadingMessage();
            showErrorMessage();
        },
        success: function(data) {
            downloadZip(data);
            hideLoadingMessage();
        },
        xhrFields: {
            responseType: 'blob'
        }
    });
}

function showLoadingMessage() {
    $("#loadingMessage").css("display", "flex");
}

function hideLoadingMessage() {
    $("#loadingMessage").css("display", "none");
}

function showErrorMessage() {
    $("#errorMessage").css("display", "flex");
}

function hideErrorMessage() {
    $("#errorMessage").css("display", "none");
}
function downloadZip(data) {
    const blob = new Blob([data], { type: 'application/zip' });
    console.info("Certificates generated successfully.")
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'certificate-files.zip';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}