/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function UiConfigEditor(containerDiv, uiConfig) {
    var self = this;
    var editor = null;
    
    function constructor() {
        
        var title = $('<h4>UI Configuration</h4>');
        containerDiv.append(title);
        
        var description = $('<p>This structure lets you configure some aspects of the user interface. Specifically the title of the service, the query that should be run when the page first loads and the list of links in the navigation bar.</p>');
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
        editor = new JsonEditorOnline(container, options, uiConfig);
    }
    
    function save() {
        var uiConfig = editor.get();
        APP.saveUiConfig(uiConfig);
    }
    
    constructor();
}