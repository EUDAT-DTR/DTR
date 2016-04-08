/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function ModalRestartDialog(promptMessageText) {
    var self = this;
    
    var modalContainerDiv = null;
    var message = null;
    var modalFooter = null;
    
    var serverRestartingBanner = null;
    
    var HEARTBEAT_TIMEOUT = 1000 * 2;
    
    function constructor() {
        modalContainerDiv = $('<div class="modal fade"></div>');
        $('body').append(modalContainerDiv);
        
        var modalDialog = $('<div class="modal-dialog">');
        modalContainerDiv.append(modalDialog);
        
        var modalContent = $('<div class="modal-content">');
        modalDialog.append(modalContent);
        
        var modalBody = $('<div class="modal-body"></div>');
        modalContent.append(modalBody);
        
        
        message = $('<p></p>');
        if (promptMessageText) {
            message.text(promptMessageText);
        } else {
            message.text("Are you sure you want to restart the server?");
        }
        
        modalBody.append(message);
        
        
        serverRestartingBanner = $('<div style="display:none"></div>'); 
        modalBody.append(serverRestartingBanner);
        
        var restartingMessage = $('<p style="text-align: center">Server Restarting. Please wait.</p>');
        serverRestartingBanner.append(restartingMessage);
        
        var img = $('<img src="img/load.gif" style="display: block; margin-left: auto; margin-right: auto">');
        serverRestartingBanner.append(img);
        
        
        modalFooter = $('<div class="modal-footer"></div>');
        modalContent.append(modalFooter);
        
        var noButton = $('<a href="#" class="btn btn-sm btn-default">No</a>');
        var yesButton = $('<a href="#" class="btn btn-sm btn-primary">Yes</a>');
        
        modalFooter.append(noButton);
        modalFooter.append(yesButton);
        
        noButton.click(onNoClick);
        yesButton.click(onYesClick);
        
        modalContainerDiv.on('hidden', destroy);
    }
    
    function onNoClick(event) {
        event.preventDefault();
        hide();
    }
    
    function onYesClick(event) {
        event.preventDefault();
        APP.restartServer(onInitalRestartResponse, onError);
        //hide();
        message.hide();
        modalFooter.hide();
        serverRestartingBanner.show();
    }
    
    function onError() {
        hide();
    }
    
    function onInitalRestartResponse(response) {
        APP.setToUnauthenticated();
        //ping to see if the server is back up
        heartbeat();
    }
    
    function heartbeat() {
        $.getJSON("sessions").done(function(resp) {
            APP.onServerRestarted();
            hide();
        }).fail(function() {
            setTimeout(heartbeat, HEARTBEAT_TIMEOUT);
        }); 
    }
    
    function show() {
        modalContainerDiv.modal('show');
    }
    
    function hide() {
        modalContainerDiv.modal('hide');
    }
    
    function destroy() {
        modalContainerDiv.remove();
    }
    
    self.show = show;
    self.hide = hide;
    
    constructor();
}