/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function ReplicationCredentialsEditor(containerDiv, credentials) {
    var self = this;
    var editor = null;
    
    function constructor() {
        var title = $('<h4>Replication Credentials</h4>');
        containerDiv.append(title);
        
        var description = $('<p>Configured on the server responding to pull replication requests. Is composed of array of objects containing the username and passwords of the requesting servers.</p>');
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
        if (!credentials) credentials = [];
        editor = new JsonEditorOnline(container, options, credentials);
    }
    
    function save() {
        var credentialsUpdate = editor.get();
        APP.saveReplicationCredentials(credentialsUpdate);
    }
    
    constructor();
}