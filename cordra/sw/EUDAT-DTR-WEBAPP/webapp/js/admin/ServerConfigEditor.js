/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function ServerConfigEditor(containerDiv) {
    var self = this;
    var editor = null;
    
    function constructor() {
        $.getJSON("/config/").done(function (configJson) {
            buildConfigEditor(configJson);
        }).fail(function (resp) {
            
        });
    }
    
    function buildConfigEditor(configJson) {
        var title = $('<h4>Server Configuration</h4>');
        containerDiv.append(title);
        
        //var description = $('<p></p>');
        //containerDiv.append(description);
        
        var toolBarDiv = $('<div class="object-editor-toolbar"></div>');
        containerDiv.append(toolBarDiv);
        
        var saveButton = $('<button class="btn btn-sm btn-default">Save Server Configuration File</button>');
        toolBarDiv.append(saveButton);
        saveButton.click(save);
        
        var editorDiv = $('<div style="height:500px;"></div>');
        containerDiv.append(editorDiv);
        
        var container = editorDiv[0];
        var options = {
                mode: 'code',
                error: function (err) {
                  alert(err.toString());
                }
              };
        editor = new JsonEditorOnline(container, options, configJson);
    }
    
    function save() {
        var config = editor.get();
        APP.saveConfig(config, onSaveSuccess);
    }
    
    function onSaveSuccess(response) {
        APP.performRestartServerWithDialog("Configuration has been saved. For the changes to take effect the server needs to restart. Do you want to restart now?")
    }
    
    function onSaveError(response) {
        
    }
    
    constructor();
}