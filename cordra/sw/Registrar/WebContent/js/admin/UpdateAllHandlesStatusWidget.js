function UpdateAllHandlesStatusWidget(containerDiv) {
    var self = this;
    var progressBar = null;
    var statusLabel = null;
    
    function constructor() {
        build();
    }
    
    function build() {
        var progressDiv = $('<div class="progress"></div>');
        progressDiv.css("margin-bottom", "0");
        progressDiv.css("margin-top", "10px");
        containerDiv.append(progressDiv);
        progressBar = $('<div class="progress-bar" role="progressbar" aria-valuenow="60" aria-valuemin="0" aria-valuemax="100" style="width: 0%"></div>');
        progressDiv.append(progressBar);
        statusLabel = $('<div></div>');
        containerDiv.append(statusLabel);
    }
    
    function setStatus(status) {
        var percent = Math.round((status.progress / status.total) * 100);
        var percentString = percent + "%";
        progressBar.width(percentString);
        
        var statusText = "";
        statusText = status.progress + "/" + status.total + " (" + percentString + ")";
        if (!status.inProgress) {
            statusText = "Complete " + statusText;
        }
        if (status.exceptionCount > 0) {
            statusText = statusText + ", " + status.exceptionCount + "errors, see server logs.";
        } 
        statusLabel.text(statusText);
    }
    self.setStatus = setStatus;
    
    function clear() {
        containerDiv.empty();
        build();
    }
    self.clear = clear;
    
    constructor();
}