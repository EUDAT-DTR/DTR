/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function DetailedObjectViewer(containerDiv, contentPlusMeta) {
    var self = this;
    
    function constructor() {
        var closeButton = $('<button class="btn btn-sm btn-default">Close</button>');
        containerDiv.append(closeButton);
        closeButton.click(onCloseClick);
        
        var digitalObjectDiv = $('<div class="well well-sm" style="background-color: #eaeaea"></div>'); 
        containerDiv.append(digitalObjectDiv);
        
        var attributesDiv = $('<div></div>');
        digitalObjectDiv.append(attributesDiv);
        
        var objectAttributesHeader = $('<h4>Object Attributes</h4>');
        attributesDiv.append(objectAttributesHeader);
        
        var table = $('<table class="table"></table>');
        attributesDiv.append(table);
        
        var colgroup = $('<colgroup><col style="width:50%"><col style="width:50%"></colgroup>');
        table.append(colgroup);
        
        var thead = $('<thead></thead>');
        table.append(thead);
        
        var titleRow = $('<tr></tr>');
        thead.append(titleRow);
        
        titleRow.append('<th>Key</th>');
        titleRow.append('<th>Value</th>');
        
        var tbody = $('<tbody></tbody>');
        table.append(tbody);
        
        addAttributeRow("Handle", contentPlusMeta.id, table);
        addAttributeRow("type", contentPlusMeta.type, table);
        
        var contentJson = JSON.stringify(contentPlusMeta.content);
        if (contentJson.length > 30) {
            contentJson = contentJson.substring(0, 30) + "...";
        }
        addAttributeRow("content", contentJson, table);
        
        var metadata = contentPlusMeta.metadata;
        for (var key in metadata) {
            var value = metadata[key];
            addAttributeRow(key, value, table);
        }
        
        if (contentPlusMeta.payloads) {
            buildPayloadViewers(contentPlusMeta.payloads, digitalObjectDiv);
        }
    }
    
    function buildPayloadViewers(payloads, digitalObjectDiv) {
        var payloadsDiv = $('<div></div>');
        digitalObjectDiv.append(payloadsDiv);
        
        var payloadAttributesHeader = $('<h4>Element Attributes</h4>');
        payloadsDiv.append(payloadAttributesHeader);
        
        for (var i = 0; i < payloads.length; i++) {
            var payload = payloads[i];
            var payloadDiv = $('<div class="media well well-sm" style="background-color: #f5f5f5"></div>');
            payloadsDiv.append(payloadDiv);
            var mediaLeft = $('<div class="media-left"></div>');
            payloadDiv.append(mediaLeft);
            
            var iconName = FileIconUtil.getFontAwesomeIconNameFor(payload.mediaType, payload.filename);
            
            var icon = $('<i class="fa fa-'+iconName+' fa-3x"></i>');
            mediaLeft.append(icon);
            var mediaBody = $('<div class="media-body"></div>');
            payloadDiv.append(mediaBody);
            
            var table = $('<table class="table"></table>');
            mediaBody.append(table);
                       
            var colgroup = $('<colgroup><col style="width:50%"><col style="width:50%"></colgroup>');
            table.append(colgroup);
            
            var thead = $('<thead></thead>');
            table.append(thead);
            
            var titleRow = $('<tr></tr>');
            thead.append(titleRow);
            
            titleRow.append('<th>Key</th>');
            titleRow.append('<th>Value</th>');
            
            var tbody = $('<tbody></tbody>');
            table.append(tbody);
            
            for (var key in payload) {
                if (key === "filename") {
                    var filename = payload[key];
                    var lastDotIndex = filename.lastIndexOf(".");
                    var mediaName = filename.substring(0, lastDotIndex);
                    var mediaExtension = filename.substring(lastDotIndex+1);
                    addAttributeRow("mediaName", mediaName, table);
                    addAttributeRow("mediaExtension", mediaExtension, table);
                } else {
                    var value = payload[key];
                    addAttributeRow(key, value, table);
                }  
            }
        }
    }
    
    function addAttributeRow(key, value, table) {
        var tr = $('<tr></tr>');
        table.append(tr);
        var tdKey = $('<td></td>');
        var tdValue = $('<td></td>');
        tdKey.text(key);
        tdValue.text(value);
        tr.append(tdKey);
        tr.append(tdValue);
    }
    
    function onCloseClick() {
        containerDiv.hide(300);
    }
    
    constructor();
}