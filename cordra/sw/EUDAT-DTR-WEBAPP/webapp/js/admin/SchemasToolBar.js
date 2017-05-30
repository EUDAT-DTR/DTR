/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function SchemasToolBar(containerDiv, schemas) {
    var self = this;
    var schemaSelect = null;
    var newSchemaObjectTypeInput = null;
    
    var addSchemaDiv = null;
    var templates = null;
    var templateSelect = null;
    
    function constructor() {
        $.getJSON("schematemplates", constructorContinuation);
    }
    
    function constructorContinuation(templatesResponse) {
        templates = templatesResponse;
        var form = $('<form class="form-inline" role="form"></form>');
        containerDiv.append(form);
        buildSchemasSelector(form);
        form.append(" ");
        buildSchemaAdder(form);
    }
    
    function buildSchemasSelector(form) {
        var editGroup = $('<div class="form-group"></div>');
        form.append(editGroup);
        
        schemaSelect = $('<select class="form-control"></select>');
        editGroup.append(schemaSelect);
        
        addSchemasToSelect();
        
        schemaSelect.on('change', onSelectChanged);
    }
    
    function addSchemasToSelect() {
        for (var objectType in schemas) {
            if ("Schema" === objectType) {
                continue;
            }
            var schema = schemas[objectType];
            var option = $('<option value="'+objectType+'">'+objectType+'</option>');
            schemaSelect.append(option);
        }
    }
    
    function onSelectChanged() {
        var objectType = schemaSelect.val();
        APP.showSchemaEditorFor(objectType);
    }
    
    function onEditClicked(e) {
        e.preventDefault();
        var selectedOption = schemaSelect.find(":selected");
        var objectType = selectedOption.val();
        APP.showSchemaEditorFor(objectType);
    }
    
    function buildSchemaAdder(form) {
        var addGroup = $('<div class="form-group"></div>');
        form.append(addGroup);
        
//        newSchemaObjectTypeInput = $('<input id="objectTypeInput" type="text" class="form-control" placeholder="New object type name">');
//        addGroup.append(newSchemaObjectTypeInput);
        
        addGroup.append("  ");
        
        var addButton = $('<button class="btn btn-sm btn-success">Add new schema</button>');
        addGroup.append(addButton);
        addButton.click(onAddClicked);
        buildAddSchemaDialog();
    }
    
    function buildAddSchemaDialog() {
        addSchemaDiv = $('<div class="modal fade" tabindex="-1"></div>');
        
        var modalDialog = $('<div class="modal-dialog"></div>');
        addSchemaDiv.append(modalDialog);
        
        var modalContent = $('<div class="modal-content"></div>');
        modalDialog.append(modalContent);
        
        var modalHeader = $('<div class="modal-header"></div>');
        modalContent.append(modalHeader);
//        var closeButton = $('<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>');
//        modalHeader.append(closeButton);

        var title = $('<h4 class="modal-title">Add Schema</h4>');
        modalHeader.append(title);
        
        var modalBody = $('<div class="modal-body"></div>');
        modalContent.append(modalBody);
        
        var addForm = $('<form class="form-horizontal" role="form"></form>');
        addForm.submit(function(e) {return false;}); 
        modalBody.append(addForm);
        
        var nameGroup = $('<div class="form-group"></div>');
        addForm.append(nameGroup);
        
        var nameLabel = $('<label for="newSchemaNameInput" class="col-sm-2 control-label">Name</label>');
        nameGroup.append(nameLabel);
        
        var nameCol = $('<div class="col-sm-10">');
        nameGroup.append(nameCol);

        
        newSchemaObjectTypeInput = $('<input id="newSchemaNameInput" type="text" class="form-control input-sm" placeholder="Schema name">');
        nameCol.append(newSchemaObjectTypeInput);

        
        var templateGroup = $('<div class="form-group"></div>');
        addForm.append(templateGroup);
        
        var templateLabel = $('<label for="templateSelect" class="col-sm-2 control-label">Template</label>');
        templateGroup.append(templateLabel);
        
        var templateCol = $('<div class="col-sm-10">');
        templateGroup.append(templateCol);
        
        templateSelect = $('<select id="templateSelect" class="form-control"></select>');
        templateCol.append(templateSelect);
        
        for (var templateName in templates) {
            var option = $('<option value="'+templateName+'">'+templateName+'</option>');
            templateSelect.append(option);
        }
        
        addSchemaDiv.on('shown.bs.modal', function () {
            newSchemaObjectTypeInput.focus();
        });
        
        var modalFooter = $('<div class="modal-footer"></div>');
        modalContent.append(modalFooter);
        
        var cancelButton = $('<button type="button" class="btn btn-sm btn-default" style="min-width: 130px;" >Cancel</button>');
        modalFooter.append(cancelButton);
        cancelButton.click(onCancelButtonClick);
        
        var addDoneButton = $('<button type="button" class="btn btn-sm btn-primary" style="min-width: 130px;" >Add</button>');
        modalFooter.append(addDoneButton);
        addDoneButton.click(onAddDoneButtonClick);
    }
    
    
    function onCancelButtonClick(e) {
        e.preventDefault();
        newSchemaObjectTypeInput.val("");
        addSchemaDiv.modal('hide');
    }
    
    function onAddDoneButtonClick(e) {
        e.preventDefault();
        var objectType = newSchemaObjectTypeInput.val();
        if (objectType === "") {
            APP.notifications.alertError("Schema name is a required.");
        } else {
            var templateName = templateSelect.val();
            var template = templates[templateName];
            var copyOfTemplate = {};
            $.extend(copyOfTemplate, template);
            
            APP.createNewSchema(objectType, copyOfTemplate);
            newSchemaObjectTypeInput.val("");
            addSchemaDiv.modal('hide');
        }
    }
    
    
    function onAddClicked(e) {
        e.preventDefault();
        addSchemaDiv.modal({ keyboard: true});
    }
    
    function refreshSelect() {
        schemaSelect.empty();
        addSchemasToSelect();
    }
    self.refreshSelect = refreshSelect;
    
    function setSelected(objectType) {
        schemaSelect.val(objectType);
    }
    self.setSelected = setSelected;
    
    constructor();
}