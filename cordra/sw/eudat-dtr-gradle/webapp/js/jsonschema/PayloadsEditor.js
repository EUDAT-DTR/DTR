"use strict";
function PayloadsEditor(containerDiv, payloads, disabled) {
    var self = this;
    var payloadsEditorsList = [];
    var payloadsContainerDiv = null;
    var deletedPayloads = [];
    var table = null;
    
    function constructor() {
        
        buildTable();
        
        payloadsContainerDiv = $('<div></div>');
        containerDiv.append(payloadsContainerDiv);
        
        containerDiv.addClass("well");
        containerDiv.addClass("well-sm");
        containerDiv.css("margin-bottom", "15px");
        if (payloads) {
            for (var i = 0; i < payloads.length; i++) {
                var payload = payloads[i];
                
                var payloadTr = $('<tr></tr>');
                table.append(payloadTr);
                
                var isNew = false;
                var payloadEditor = new PayloadEditor(self, payloadTr, payload, disabled, isNew);
                payloadsEditorsList.push(payloadEditor);
            }
        }
        
        if (!disabled) {
            var addPayloadButton = $('<button class="btn btn-sm btn-default" style="margin-top: 10px">Add Payload</button>');
            containerDiv.append(addPayloadButton);
            addPayloadButton.click(addNewPayload);
        }
    }
    
    function buildTable() {
        table = $('<table class="table"></table>');
        table.css("margin-bottom", "0");
        containerDiv.append(table);
        
        var colgroup = $('<colgroup><col style="width:25%"><col style="width:25%"><col style="width:25%"><col style="width:15%"><col style="width:10%"></colgroup>');
        table.append(colgroup);
        
        var thead = $('<thead></thead>');
        table.append(thead);
        
        var titleRow = $('<tr></tr>');
        thead.append(titleRow);
        
        titleRow.append('<th>Payloads</th>');
        titleRow.append('<th></th>');
        titleRow.append('<th></th>');
        titleRow.append('<th></th>');
        titleRow.append('<th></th>');
        
        var tbody = $('<tbody></tbody>');
        table.append(tbody);
    }
    
    function addNewPayload(e) {
        e.preventDefault();
        
        var payloadTr = $('<tr></tr>');
        table.append(payloadTr);
        
        var isNew = true;
        var payloadEditor = new PayloadEditor(self, payloadTr, null, false, isNew);
        payloadsEditorsList.push(payloadEditor);
    }
    
    function deletePayload(payloadEditor) {
        var i = $.inArray(payloadEditor, payloadsEditorsList);
        if (i < 0) return;
        payloadsEditorsList.splice(i, 1);
        if (!payloadEditor.isNew()) {
            if ($.inArray(deletedPayloads, payloadEditor.getName()) < 0) {
                deletedPayloads.push(payloadEditor.getName());
            }
        }
    }
    self.deletePayload = deletePayload;
    
    // TODO validate duplicated payload name?
    
    function appendFormData(formData) {
        cleanUpDeletedPayloads();
        for (var i = 0; i < deletedPayloads.length; i++) {
            formData.append("payloadToDelete", deletedPayloads[i]);
        }
        for (var i = 0; i < payloadsEditorsList.length; i++) {
            var payloadEditor = payloadsEditorsList[i];
            var blob = payloadEditor.getBlob();
            if (blob) { 
                formData.append(payloadEditor.getName(), blob);
            }
        }
    }
    self.appendFormData = appendFormData;

    // clean up deletedPayloads before building HTTP request, in case someone deletes then re-adds a payload of the same name
    function cleanUpDeletedPayloads() {
        var map = {};
        for (var i = 0; i < deletedPayloads.length; i++) {
            map[deletedPayloads[i]] = true;
        }
        for (var i = 0; i < payloadsEditorsList.length; i++) {
            delete map[payloadsEditorsList[i].getName()];
        }
        deletedPayloads = Object.keys(map);
    }
    
    constructor();
}