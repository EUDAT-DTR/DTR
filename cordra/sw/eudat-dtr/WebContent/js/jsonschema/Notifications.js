/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function Notifications(div) {
    var self = this;

    function constructor() {

    }

    function isDiv(message) {
        if (message.jquery === undefined) {
            return false;
        } else if (!message.is('div')) {
            return false;
        } else {
            return true;
        }
    }
    
    function alertWarning(messageText) {
        div.empty();
        var warningContainerDiv = $('<div class="alert alert-warning">'
                + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                + '<strong>Warning: </strong></div>');
        div.append(warningContainerDiv);
        var messageSpan = $('<span></span>');
        messageSpan.text(messageText);
        warningContainerDiv.append(messageSpan);
    }
    self.alertWarning = alertWarning;

    function alertError(messageText) {
        div.empty();
        var errorContainerDiv = $('<div class="alert alert-danger">'
                + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                + '<strong>Error: </strong></div>');
        div.append(errorContainerDiv);
        var messageSpan = $('<span></span>');
        messageSpan.text(messageText);
        errorContainerDiv.append(messageSpan);
    }
    self.alertError = alertError;

    function alertSuccess(messageText) {
        div.empty();
        var successContainerDiv = $('<div class="alert alert-success">'
                + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                + '<strong>Success: </strong></div>');
        div.append(successContainerDiv);
        var messageSpan = $('<span></span>');
        messageSpan.text(messageText);
        successContainerDiv.append(messageSpan);
    }
    self.alertSuccess = alertSuccess;

    function alertInfo(messageText) {
        div.empty();
        var infoContainerDiv = $('<div class="alert alert-info">'
                + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                + '<strong>Heads Up: </strong></div>');
        div.append(infoContainerDiv);
        var messageSpan = $('<span></span>');
        messageSpan.text(messageText);        
        infoContainerDiv.append(messageSpan);
    }    
    self.alertInfo = alertInfo;   
    
    function alertWarningDiv(messageDiv) {
        div.empty();
        if (!isDiv(messageDiv)) {
            console.log("An attempt to set an invalid notification was made." + messageDiv);
        } else {
            var warningContainerDiv = $('<div class="alert alert-warning">'
                    + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                    + '<strong>Warning: </strong></div>');
            div.append(warningContainerDiv);
            warningContainerDiv.append(messageDiv);
        }
    }
    self.alertWarningDiv = alertWarningDiv;     

    function alertErrorDiv(messageDiv) {
        div.empty();
        if (!isDiv(messageDiv)) {
            console.log("An attempt to set an invalid notification was made." + messageDiv);
        } else {
            var errorContainerDiv = $('<div class="alert alert-danger">'
                    + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                    + '<strong>Error: </strong></div>');
            div.append(errorContainerDiv);
            errorContainerDiv.append(messageDiv);
        }
    }
    self.alertErrorDiv = alertErrorDiv;    

    function alertSuccessDiv(messageDiv) {
        div.empty();
        if (!isDiv(messageDiv)) {
            console.log("An attempt to set an invalid notification was made." + messageDiv);
        } else {
            var successContainerDiv = $('<div class="alert alert-success">'
                    + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                    + '<strong>Success: </strong></div>');
            div.append(successContainerDiv);
            successContainerDiv.append(messageDiv);
        }
    }
    self.alertSuccessDiv = alertSuccessDiv;

    function alertInfoDiv(messageDiv) {
        div.empty();
        if (!isDiv(messageDiv)) {
            console.log("An attempt to set an invalid notification was made." + messageDiv);
        } else {
            var infoContainerDiv = $('<div class="alert alert-info">'
                    + '<button type="button" class="close" data-dismiss="alert">&times;</button>'
                    + '<strong>Heads Up: </strong></div>');
            div.append(infoContainerDiv);
            infoContainerDiv.append(messageDiv);
        }
    }
    self.alertInfoDiv = alertInfoDiv;

    function clear() {
        div.empty();
    }
    self.clear = clear;

    constructor();
};