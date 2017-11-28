/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function ObjectAclEditor(containerDiv, acl, objectId) {
    var self = this;
    var editor = null;
    
    function constructor() {
        var saveAclButton = $('<button class="btn btn-sm btn-default">Save ACL</button>');
        containerDiv.append(saveAclButton);
        saveAclButton.click(onSaveAclClick);
        containerDiv.append(" ");
        
        var cancelButton = $('<button class="btn btn-sm btn-default">Cancel</button>');
        containerDiv.append(cancelButton);
        cancelButton.click(cancelEditAclClick);
        
        editorDiv = $('<div></div>');
        containerDiv.append(editorDiv);
        
        var options = {
                theme : "bootstrap3",
                schema : getAclJsonSchema(),
                startval : acl,
                disable_edit_json : true,
                disable_properties : true,
//                required_by_default : true,
                disable_collapse: true,
                disabled: false
        };
        JSONEditor.defaults.options.iconlib = 'bootstrap3';
        JSONEditor.defaults.editors.object.options.disable_properties = true;
        JSONEditor.defaults.editors.object.options.disable_edit_json = true;
        JSONEditor.defaults.editors.object.options.disable_collapse = false;
        
        editor = new JSONEditor(editorDiv[0], options);
        editor.on('change', onChange);
    }
    
    function onChange() {
        
    }
    
    function onSaveAclClick() {
        var newAcl = editor.getValue();
        APP.saveAclForCurrentObject(newAcl, onSaveAclSuccess, onSaveAclFail);
    }
    
    function cancelEditAclClick() {
        containerDiv.hide(300);
    }
    
    function onSaveAclSuccess(res, status, xhr) {
        containerDiv.hide(300);
    }

    function onSaveAclFail(res, status, xhr) {
        console.log("acl not saved");
    }
    
    function getAclJsonSchema() {
        var aclJsonSchema = {
                "type": "object",
                "title": "Access Control List",
                "required": [
                             ],
                             "properties": {
                                 "read": {
                                     "type": "array",
                                     "format": "table",
                                     "title": "Readers",
                                     "uniqueItems": true,
                                     "items": {
                                         "type": "string",
                                         "title": "Reader",
                                         "net.cnri.repository": {
                                             "type": {
                                                 "handleReference": {
                                                     "types": ["User", "Group"]
                                                 }
                                             }
                                         }
                                     }
                                 },
                                 "write": {
                                     "type": "array",
                                     "format": "table",
                                     "title": "Writers",
                                     "uniqueItems": true,
                                     "items": {
                                         "type": "string",
                                         "title": "Writer",
                                         "net.cnri.repository": {
                                             "type": {
                                                 "handleReference": {
                                                     "types": ["User", "Group"]
                                                 }
                                             }
                                         }        
                                     }
                                 }
                             }
        };
        return aclJsonSchema;
    }
    
    function destroy() {
        editor.destroy();
    }
    self.destroy = destroy;
    
    constructor();
}