/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function Notifications(div) {
    var self = this;

    function constructor() {
        toastr.options = {
                "closeButton": false,
                "debug": false,
                "newestOnTop": false,
                "progressBar": false,
                "positionClass": "toast-bottom-full-width",
                "preventDuplicates": false,
                "onclick": null,
                "showDuration": "300",
                "hideDuration": "1000",
                "timeOut": "5000",
                "extendedTimeOut": "1000",
                "showEasing": "swing",
                "hideEasing": "linear",
                "showMethod": "fadeIn",
                "hideMethod": "fadeOut"
              };
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
        toastr.warning(messageText);
    }
    self.alertWarning = alertWarning;

    function alertError(messageText) {
        toastr.error(messageText);
    }
    self.alertError = alertError;

    function alertSuccess(messageText) {
        toastr.success(messageText);
    }
    self.alertSuccess = alertSuccess;

    function alertInfo(messageText) {
        toastr.info(messageText);
    }    
    self.alertInfo = alertInfo;   
    
    function alertWarningDiv(messageDiv) {
        console.log("alertWarningDiv not supported");
    }
    self.alertWarningDiv = alertWarningDiv;     

    function alertErrorDiv(messageDiv) {
        console.log("alertErrorDiv not supported");
    }
    self.alertErrorDiv = alertErrorDiv;    

    function alertSuccessDiv(messageDiv) {
        console.log("alertSuccessDiv not supported");
    }
    self.alertSuccessDiv = alertSuccessDiv;

    function alertInfoDiv(messageDiv) {
        console.log("alertInfoDiv not supported");
    }
    self.alertInfoDiv = alertInfoDiv;

    function clear() {
        //no-op
    }
    self.clear = clear;

    constructor();
};