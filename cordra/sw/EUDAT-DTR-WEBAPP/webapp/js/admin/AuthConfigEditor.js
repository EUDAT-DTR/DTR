/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function AuthConfigEditor(containerDiv, authConfig) {
    var self = this;
    var editor = null;
    var adminPasswordInput = null;
    
    function constructor() {
        var title = $('<h4>Default Access Control</h4>');
        containerDiv.append(title);
        
        var description = $('<p>The type-level and default ACLs are configured by specifying a JSON structure. ACLs can use the reserved key words; public, authenticated, creator, self and admin.</p>');
        containerDiv.append(description);
        
        var toolBarDiv = $('<div class="object-editor-toolbar"></div>');
        containerDiv.append(toolBarDiv);
        
        var saveButton = $('<button class="btn btn-sm btn-default">Save</button>');
        toolBarDiv.append(saveButton);
        saveButton.click(save);
        
        var editorDiv = $('<div style="height:500px;"></div>');
        containerDiv.append(editorDiv);
        
        var container = editorDiv[0];
        var options = {
                mode: 'code',
                modes: ['code', 'tree'], // allowed modes
                error: function (err) {
                  alert(err.toString());
                }
              };
        if (!authConfig) {
            authConfig = {};
        }
        
        editor = new JsonEditorOnline(container, options, authConfig);
    }
    
    function save() {
        var authConfigUpdate = editor.get();
        APP.saveAuthConfig(authConfigUpdate);
    }
    
    constructor();
}