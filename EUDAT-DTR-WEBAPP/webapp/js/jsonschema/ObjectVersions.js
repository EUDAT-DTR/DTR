/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function ObjectVersions(containerDiv, versions, objectId, allowEdits) {
    var self = this;
    var versionsListDiv = null;
    
    function constructor() {
        if (allowEdits) {
            var publishButton = $('<button class="btn btn-sm btn-default">Make Version</button>');
            containerDiv.append(publishButton);
            publishButton.click(onPublishClick);        
            containerDiv.append(" ");
        }
        
        var closeButton = $('<button class="btn btn-sm btn-default">Close</button>');
        containerDiv.append(closeButton);
        closeButton.click(onCloseClick);
        
        versionsListDiv = $('<div></div>');
        containerDiv.append(versionsListDiv);
        refreshList(versions);
    }
    
    function onCloseClick() {
        containerDiv.hide(300);
    }
    
    function onPublishClick() {
        publishVersion();
    }
    
    function refreshList(versionsList) {
        sortByDate(versionsList);
        
        versionsListDiv.empty();
        if (versionsList.length == 0 || versionsList.length == 1) {
            versionsListDiv.append('<p>No versions</p>');
        } else {
            var table = $('<table class="table"></table>');
            versionsListDiv.append(table);
            
            //var colgroup = $('<colgroup><col style="width:50%"><col style="width:50%"></colgroup>');
            //table.append(colgroup);
            
            var thead = $('<thead></thead>');
            table.append(thead);
            
            var titleRow = $('<tr></tr>');
            thead.append(titleRow);
            
            titleRow.append('<th>Versioned on</th>');
            titleRow.append('<th>Versioned by</th>');
            titleRow.append('<th>Id</th>');
            
            var tbody = $('<tbody></tbody>');
            table.append(tbody);
            
            for (var i = 0; i < versionsList.length; i++) {
                var versionInfo = versionsList[i];

                var versionDiv = $('<div></div>');
                versionsListDiv.append(versionDiv);
                
                var tr = $('<tr></tr>');
                table.append(tr);
                
                var tdPublishedOn = $('<td></td>');
                var tdPublishedBy = $('<td></td>');
                
                if (versionInfo.isTip) {
                    var modifiedOn = new Date(versionInfo.modifiedOn).toISOString();
                    
                    tdPublishedOn.text(modifiedOn + " (Latest)");
                    tdPublishedBy.text("");
                } else {
                    var publishedOn = new Date(versionInfo.publishedOn).toISOString();
                    tdPublishedOn.text(publishedOn);
                    tdPublishedBy.text(versionInfo.publishedBy);
                }
                
                tr.append(tdPublishedOn);
                tr.append(tdPublishedBy);
                
                var link = $('<a style="display:inline-block" target="_blank">').attr('href', 'objects/' + versionInfo.id).text(versionInfo.id);
                link.attr('data-handle', versionInfo.id);
                link.click(onHandleClick);
                
                var tdId = $('<td></td>');
                tdId.append(link);
                
                if (versionInfo.id === objectId) {
                    tr.addClass("info");
                    var thisObjectFlag = $('<span></span>');
                    thisObjectFlag.text(" This " + versionInfo.type);
                    tdId.append(thisObjectFlag);
                }
                tr.append(tdId);
            }
        }
    }
    
    function onHandleClick(e) {
        e.preventDefault();
        var link = $(this);
        var handle = link.attr('data-handle');
        APP.resolveHandle(handle);
    }
    
    function addTableRow(table, values) {
        var tr = $('<tr></tr>');
        table.append(tr);
        for (var i = 0; i < values.length; i++) {
            var value = values[i];
            var td = $('<td></td>');
            td.text(value);
            tr.append(td);
        }
    }
    
    function sortByDate(versionsList) {
        versionsList.sort(compare);
        versionsList.reverse();
    }
    
    function compare(a, b) {
        if (!a.publishedOn) {
            return 1;
        }
        if (!b.publishedOn) {
            return -1;
        }
        if (a.publishedOn < b.publishedOn) {
            return -1;
        }
        if (a.publishedOn > b.publishedOn) {
            return 1;
        }
        return 0;
    }
    
    function getVersions() {
        APP.getVersionsFor(objectId, onGotVersionsSuccess, onGotVersionsError);
    }
    
    function onGotVersionsSuccess(response) {
        refreshList(response);
    }
    
    function onGotVersionsError(response) {
        
    }
    
    function publishVersion() {
        APP.publishVersion(objectId, onPublishVersionSuccess, onPublishVersionError);
    }
    
    function onPublishVersionSuccess() {
        getVersions();
    }
    
    function onPublishVersionError() {
        
    }
    
    constructor();
}