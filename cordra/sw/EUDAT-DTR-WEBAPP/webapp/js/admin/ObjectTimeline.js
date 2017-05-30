/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function ObjectTimeline(containerDiv) {
    var self = this;
    var groups = null;
    var items = null;
    
    var SECOND = 1000;
    var MIN = SECOND * 60;
    var HOUR = MIN * 60;
    var DAY = HOUR * 24;
    var WEEK = DAY * 7;
    
    var groupCount = 0;
    
    var handleToGroupIdMap = {};
    
    function constructor() {
        groups = new vis.DataSet();
        items = new vis.DataSet();
        
        var timelineContainer = $('<div></div>');
        containerDiv.append(timelineContainer);
        
        var container = timelineContainer[0];
        
        var options = {
                groupOrder: 'content'  // groupOrder can be a property name or a sorting function
        };
        
        var timeline = new vis.Timeline(container);
        timeline.setOptions(options);
        timeline.setGroups(groups);
        timeline.setItems(items);
        
        var now = new Date().getTime();
        var end = now;
        var start = now - WEEK *2;
        
        queryForObjects(start, end);
    }
    
    function queryForObjects(start, end) {
        var query = "objatt_internal.created:["+start+" TO "+end+"]";
        APP.search(query, 0, 0, null, addObjectsToTimeline, onError);
    }
    
    function addObjectsToTimeline(response) {
        var itemsArray = [];
        for (var i = 0; i < response.results.length; i++) {
            var result = response.results[i];
            if (!result.metadata.createdBy) {
                continue;
            }
            var groupId = handleToGroupIdMap[result.metadata.createdBy];
            if (!groupId) {
                var group = {
                        id: groupCount, 
                        text: result.metadata.createdBy
                };
                groups.add(group);
                handleToGroupIdMap[result.metadata.createdBy] = groupCount;
                groupCount++;
            }
            groupId = handleToGroupIdMap[result.metadata.createdBy];
            var item = {
                    id: result.id,
                    group: groupId,
                    content: result.type,
                    start: result.metadata.createdOn,
                    type: 'box'
            };
            itemsArray.push(item);
        }
        items.add(itemsArray);    
           // content: 'item ' + i + ' <span style="color:#97B0F8;">(' + result.type + ')</span>',
    }
    
    function onError() {}
    
    
    constructor();
}