/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function RemoteRepositoriesEditor(containerDiv, remoteRepositories) {
    var self = this;
    var editor = null;
    
    function constructor() {
        var title = $('<h4>Replication</h4>');
        containerDiv.append(title);
        
        var description = $('<p>The DO Repository servers can be configured to replicate from other DO Repository servers. Such a configuration is defined on the server that does the pulling. The replication configuration is stored as multiple digital objects: one object per server being pulled from. Each of those objects define the base URI of the DO Repository server, the type of objects to be pulled, the type of objects to be specifically omitted, username and password of the account holder who has privileges on the remote server. baseUri, includeTypes, excludeTypes, username, and password properties are used to capture each of those configuration parameters in the JSON configuration below.</p>');
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
        editor = new JsonEditorOnline(container, options, remoteRepositories);
    }
    
    function save() {
        var remoteRepositoriesUpdate = editor.get();
        APP.saveRemoteRepositories(remoteRepositoriesUpdate);
    }
    
    constructor();
}