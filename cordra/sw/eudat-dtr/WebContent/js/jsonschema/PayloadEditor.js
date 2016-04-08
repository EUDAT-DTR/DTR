/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function PayloadEditor(parentPayloadsEditor, payloadTr, payload, disabled, isNewParam) {
    
    var self = this;
    var nameInput = null;
    var nameLabel = null;
    var fileInput = null;
    
    var tdName = null;
    var tdFilename = null;
    var tdFileInput = null;
    var tdSize = null;
    var tdDelete = null;
    
    function constructor() {
        tdName = $('<td></td>');
        tdFilename = $('<td></td>');
        tdFileInput = $('<td></td>');
        tdSize = $('<td></td>');
        tdDelete = $('<td></td>');
        
        payloadTr.append(tdName);
        payloadTr.append(tdFilename);
        payloadTr.append(tdFileInput);
        payloadTr.append(tdSize);
        payloadTr.append(tdDelete);
        
        self.objectId = APP.getObjectId();
        
        fileInput = $('<input/>');
        fileInput.attr('type', 'file');

        buildControls(payload);
        
        if (!disabled) {
            tdFileInput.append(fileInput);
            prettifyThisFileInput(fileInput);
        }
    }
    
    function isNew() {
        return isNewParam;
    }
    self.isNew = isNew;
    
    function getName() {
        if (payload) {
            return payload.name;
        } else {
            return nameInput.val();
        }
    }
    self.getName = getName;
    
    function getBlob() {
        return fileInput[0].files[0];
    }
    self.getBlob = getBlob;
    
    function buildControls(payload) {
        //containerDiv.empty();
        if (!disabled) {
            var closeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>');
            tdDelete.append(closeButton);
            closeButton.click(onCloseClick);
        }
        if (isNew()) {
            nameInput = $('<input type="text" style="width:100%"></input>');
            tdName.append(nameInput);
            nameInput.focus();
        } else {
            nameLabel = $('<span></span>');
            tdName.append(nameLabel);
            nameLabel.text(payload.name);

            var iconName = FileIconUtil.getFontAwesomeIconNameFor(payload.mediaType, payload.filename);
            var icon = $('<i class="fa fa-'+iconName+' fa-1x"></i>');
            tdFilename.append(icon);
            tdFilename.append(" ");
            
            var sizeLabel = $('<span></span>');
            sizeLabel.text(payload.size + " bytes");
            tdSize.append(sizeLabel);
            
            var downloadButton = buildDownloadButton(payload.filename);
            tdFilename.append(downloadButton);
            
            
        }
    }
    
    function onCloseClick() {
        parentPayloadsEditor.deletePayload(self);
        payloadTr.remove();
    }
    
    function buildDownloadButton(text) {
        var form = $('<form style="display:none" method="POST"/>');
        form.attr("action", getDownloadUrl());
        var csrfInput = $('<input type="hidden" name="csrfToken"/>');
        csrfInput.val(APP.getCsrfCookieToken());
        form.append(csrfInput);
        var downloadButton = $('<a href="#"></a>');
        if (text) {
            downloadButton.text(text);
        } else {
            downloadButton.text("Download");
        }
        downloadButton.click(function (event) {
            event.preventDefault();
            form.submit();
        });
        downloadButton.append(form);
        return downloadButton;
    }
    
    function getDownloadUrl() {
        return 'objects/' + self.objectId + '?payload=' + encodeURIComponent(payload.name).replace(/%2F/g,"/") + "&disposition=attachment";
    }
    
    function prettifyThisFileInput(fileInput) {
//        if (navigator.userAgent.search("Safari") >= 0 && navigator.userAgent.search("Chrome") < 0) return;
        fileInput = $(fileInput);
//        if(fileInput.css('left')==='-1999px') return;
//        fileInput.css('left','-1999px');
        if (fileInput.css('opacity') === '0') return;
        fileInput.css('opacity', '0');
        fileInput.css('z-index', '-100');
        fileInput.css('position','fixed');
        fileInput.css('left', '-10px');
        fileInput.css('height', '1px');
        fileInput.css('width', '1px');
        fileInput.css('margin', '0');
        fileInput.css('padding', '0');
        var textForButton = "Choose file";
        var button = $('<button class="btn btn-sm btn-default" type="button"></button>');
        
        button.text(textForButton);
        var span = $('<span class="help-inline">No files chosen</span>');
        var div = $('<div class="hide-with-buttons"/>');
        div.append(button, " ", span);
        fileInput.before(div);
        button.off('click').click(function(event) {
            event.stopImmediatePropagation();
            fileInput.click(); 
        });
        fileInput.change(function() { 
            if(fileInput[0].files.length===0) {
                span.text('No files chosen');
            } else if(fileInput[0].files.length===1) {
                span.text(fileInput[0].files[0].name);
            } else {
                span.text(fileInput[0].files.length + ' files');
            }
        });
    }    
    
    constructor();
}

window.PayloadEditor = PayloadEditor;

})();