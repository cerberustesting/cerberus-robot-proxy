document.addEventListener('DOMContentLoaded', function () {
    el("notBeforeDate").value = new Date().toISOString().split('T')[0];
    el("notAfterDate").value = new Date(new Date().getTime() + (730 * 24 * 60 * 60 * 1000)).toISOString().split('T')[0];
    el("generateCertsButton").addEventListener('click', function () {
        generateCerts();
    });
});

function generateCerts() {
    //Avoid to call when the password is empty
    if (el("password").value === "") {
        return false;
    }

    console.info("Request for generating certificates sent to the server.");
    hideErrorMessage();
    showLoadingMessage();

    fetch("certs/generate", {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            commonName: encodeURIComponent(el("commonName").value),
            organization: encodeURIComponent(el("organization").value),
            notBeforeDate: encodeURIComponent(new Date(el("notBeforeDate").value).toISOString()),
            notAfterDate: encodeURIComponent(new Date(el("notAfterDate").value).toISOString()),
            password: encodeURIComponent(el("password").value)
        })
    }).then(function (response) {
        if (!response.ok) {
            throw new Error("Request failed (" + response.status + ")");
        }
        return response.blob();
    }).then(function (data) {
        downloadZip(data);
        hideLoadingMessage();
    }).catch(function (error) {
        console.error("An error occurred during generation of certificates " + error);
        hideLoadingMessage();
        showErrorMessage();
    });
}

function showLoadingMessage() {
    el("loadingMessage").style.display = "flex";
}

function hideLoadingMessage() {
    el("loadingMessage").style.display = "none";
}

function showErrorMessage() {
    el("errorMessage").style.display = "flex";
}

function hideErrorMessage() {
    el("errorMessage").style.display = "none";
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
