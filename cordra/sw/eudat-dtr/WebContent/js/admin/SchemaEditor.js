/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function SchemaEditor(containerDiv, schema, type) {
    var self = this;
    var editorDiv = null;
    var toolBarDiv = null;
    var editor = null;
    var previewDiv = null;
    
    function constructor() {
//        var title = $('<h4>Schemas Editor</h4>');
//        containerDiv.append(title);
        
        
        toolBarDiv = $('<div class="object-editor-toolbar"></div>');
        containerDiv.append(toolBarDiv);
        
        var saveButton = $('<button class="btn btn-sm btn-default">Save</button>');
        toolBarDiv.append(saveButton);
        saveButton.click(save);
        
        var deleteButton = $('<button class="btn btn-sm btn-danger">Delete</button>');
        toolBarDiv.append(deleteButton);
        deleteButton.click(deleteSchema);
        
        toolBarDiv.append(" ");
        
        var previewButton = $('<button class="btn btn-sm btn-default">Preview</button>');
        toolBarDiv.append(previewButton);
        previewButton.click(previewClick);
        
        var typeLabel = $('<label></label>');
        typeLabel.text("Object Type: " +type);
        containerDiv.append(typeLabel);
        
        editorDiv = $('<div style="height:500px;"></div>');
        containerDiv.append(editorDiv);
        
        var container = editorDiv[0];
        var options = {
                mode: 'code',
                modes: ['code', 'tree'], // allowed modes
                error: function (err) {
                  alert(err.toString());
                }
              };
        editor = new JsonEditorOnline(container, options, schema);
        
        previewDiv = $('<div></div>');
        containerDiv.append(previewDiv);
    }
    
    function save() {
        var schema = editor.get();
        APP.saveSchema(schema, type);
    }
    
    function deleteSchema() {
        APP.deleteSchema(type);
    }
    
    function previewClick() {
        previewDiv.empty();
        var schema = editor.get();
        var previewEditor = PreviewObjectEditor(previewDiv, schema, type, null, "example/id");
    }
    
    constructor();
}