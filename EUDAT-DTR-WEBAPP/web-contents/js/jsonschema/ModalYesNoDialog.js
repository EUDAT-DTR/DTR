/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function ModalYesNoDialog(msg, yesCallback, noCallback, caller) {
	var self = this;
	
	var modalContainerDiv = null;
	
	function constructor() {
		modalContainerDiv = $('<div class="modal fade"></div>');
		$('body').append(modalContainerDiv);
		
		var modalDialog = $('<div class="modal-dialog">');
		modalContainerDiv.append(modalDialog);
		
	    var modalContent = $('<div class="modal-content">');
	    modalDialog.append(modalContent);
		
		var modalBody = $('<div class="modal-body"></div>');
		modalContent.append(modalBody);
		var message = $('<p></p>');
		message.text(msg);
		modalBody.append(message);
		
		var modalFooter = $('<div class="modal-footer"></div>');
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
	    noCallback.call(caller); 
	    hide();
	}
	
	function onYesClick(event) {
	    event.preventDefault();
	    yesCallback.call(caller);
	    hide();
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