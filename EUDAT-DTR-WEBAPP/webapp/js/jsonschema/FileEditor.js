/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function FileEditor($fileInput, editor) {
    
    var self = this;
    var controlsDiv = null;
    var jsonPointer = null;
    
    function constructor() {
        jsonPointer = getJsonPointerFromEditor(editor);
        self.objectId = APP.getObjectId();
        
        controlsDiv = $('<div></div>');
        $fileInput.after(controlsDiv);
        prettifyThisFileInput($fileInput);
        
        if (self.objectId != null) {
            APP.getFileMetadata(self.objectId, jsonPointer, onGotMetadata);
        }
    }
    
    function buildControls(metadata) {
        controlsDiv.empty();
        var form = $('<form style="display:none" method="POST"/>');
        form.attr("action", getDownloadUrl());
        var csrfInput = $('<input type="hidden" name="csrfToken"/>');
        csrfInput.val(APP.getCsrfCookieToken());
        form.append(csrfInput);
        var downloadButton = $('<a class="btn btn-sm">Download</a>');
        downloadButton.click(function (event) {
            event.preventDefault();
            form.submit();
        });
        controlsDiv.append(form);
        controlsDiv.append(downloadButton);
        controlsDiv.append(" ");
        var fileNameText = $('<span></span>');
        fileNameText.text(metadata.filename);
        controlsDiv.append(fileNameText);
    }
    
    function getDownloadUrl() {
        return 'objects/' + self.objectId + '?jsonPointer=' + encodeURIComponent(jsonPointer).replace(/%2F/g,"/") + "&disposition=attachment";
    }
    
    function onGotMetadata(metadataResponse, status, xhr) {
        editor.cnriFileExists = true;
        var parent = editor.parent;
        while (parent) { 
            parent.refreshValue();
            parent = parent.parent;
        }
        buildControls(metadataResponse);
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
        var button = $('<button class="btn btn-default" type="button"></button>');
        
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

function getJsonPointerFromEditor(editor) {
    var path = editor.path;
    if (path === 'root') return '';
    var parent = editor.parent;
    var parentPath = parent.path;
    if (path === parentPath) return getJsonPointerFromEditor(parent);
    var key = path.substring(parentPath.length + 1);
    return getJsonPointerFromEditor(parent) + '/' + JsonUtil.encodeJsonPointerSegment(key);
}
FileEditor.Testing = FileEditor.Testing || {};
FileEditor.Testing.getJsonPointerFromEditor = getJsonPointerFromEditor;

window.FileEditor = FileEditor;

})();