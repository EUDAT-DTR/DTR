/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function RelationshipsGraph(containerDiv, objectId) {
    var self = this;
    
    var defaultColor = {border: "#2B7CE9", background: "#97C2FC", highlight: {border: "#2B7CE9", background: "#D2E5FF"}, hover: {border: "#2B7CE9", background: "#D2E5FF"}};
    var selectedColor = {background: "Silver"};
    
    var nodes = null;
    var edges = null;
    var network = null;
    var maxPreviewStringLength = 20;
    
    var instructions = null;
    var referrersLink = null;
    
    var canvasDiv = null;
    var resizeButton = null;
    var isBig = false;
    
    var existingEdges = {};
    var existingNodes = {};
    
    var currentSelectedNode = null;
    var selectedDetails = null;
    
    var fanOutAllButton = null;
    var fanOutSelectedButton = null;
    var inboundToggleButton = null;
    var outboundOnly = true;
    
    var undoFanOutButton = null;
    
    function constructor() {
        var closeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-remove"></span></button>');
        containerDiv.append(closeButton);
        closeButton.click(onCloseClick);

        resizeButton = $('<button class="btn btn-sm btn-default pull-right"><span class="glyphicon glyphicon-resize-full"></span></button>');
        containerDiv.append(resizeButton);
        resizeButton.click(onResizeClick);

        undoFanOutButton = $('<button class="btn btn-sm btn-default pull-right">Undo Fan Out</button>');
        containerDiv.append(undoFanOutButton);
        undoFanOutButton.click(deleteLastAddedItems);
        
        fanOutAllButton = $('<button class="btn btn-sm btn-default pull-right">Fan Out All</button>');
        containerDiv.append(fanOutAllButton);
        fanOutAllButton.click(onFanOutAllClick);
        
        fanOutSelectedButton = $('<button class="btn btn-sm btn-default pull-right">Fan Out</button>');
        containerDiv.append(fanOutSelectedButton);
        fanOutSelectedButton.click(onFanOutSelectedClick);
        
        inboundToggleButton = $('<button class="btn btn-sm btn-default pull-right">Restart with inbound links</button>');
        containerDiv.append(inboundToggleButton);
        inboundToggleButton.click(inboundToggleButtonClick);
        
        instructions = $('<div style="margin:5px 5px 5px 10px">Click and drag to manipulate graph.  Double click to load object.</div>');
        containerDiv.append(instructions);
        
        addReferrersLinkIfNeeded();
        
        var clearFix = $('<div class="clearfix"></div>');
        containerDiv.append(clearFix);
        
        canvasDiv = $('<div style="height:730px"></div>');
        containerDiv.append(canvasDiv);
        canvasDiv.css('visibility', 'hidden');
        
        selectedDetails = $('<div style="margin:5px 5px 0px 10px"></div>');
        containerDiv.append(selectedDetails);
        
        var requestedLevel = 1;
        APP.getRelationships(objectId,  function (response) { onGotRelationshipsSuccess(response, requestedLevel); } , onGotRelationshipsError, outboundOnly);
    }
    
    function addReferrersLinkIfNeeded() {
    	if (referrersLink) {
    		referrersLink.remove();
    		referrersLink = null;
    	}
        var referrersQuery = "internal.pointsAt:" + objectId;
        APP.search(referrersQuery, 0, 1, null, function (response) {
            APP.notifications.clear();
        	if (response.size !== 0) {
        		addReferrersLink();
        	}
        }, function (response) {
            APP.notifications.alertError(response.message);
        });
    }
    
    function addReferrersLink() {
    	referrersLink = $('<span/>');
    	referrersLink.append('<br/>');
    	referrersLink.append("There are objects which refer to this object.  ");
    	var link = $('<a href="#">Click here</a>');
    	referrersLink.append(link);
    	referrersLink.append(" to list them.");
    	instructions.append(referrersLink);
    	link.click(function (e) {
    		e.preventDefault();
            var referrersQuery = "internal.pointsAt:" + objectId;
            APP.performSearchWidgetSearch(referrersQuery);
    	});
    }
    
    function getCurrentTopLevel() {
        var topLevel = 0;
        var nodeIds = nodes.getIds();
        for (var i = 0; i < nodeIds.length; i++) {
            var nodeId = nodeIds[i];
            var node = nodes.get(nodeId);
            if (node.level > topLevel) {
                topLevel = node.level;
            }
        }
        
        var edgeIds = edges.getIds();
        for (var i = 0; i < edgeIds.length; i++) {
            var edgeId = edgeIds[i];
            var edge = edges.get(edgeId);
            if (edge.level > topLevel) {
                topLevel = edge.level;
            }
        }
        return topLevel;
    }
    
    function buildNetwork() {
        var data = {
                nodes: nodes,
                edges: edges
        };
        var options = {
                physics: {
                    hierarchicalRepulsion: {
                        nodeDistance: 500
                    }
                },
                hierarchicalLayout: {
                    enabled:true,
                    direction: "UD",
                    levelSeparation : 200,
                    nodeSpacing : 300
                },
                nodes: {
                    shape: 'box', 
                    maxWidth: 200
                },
                edges: {
                    length: 500
                },
                stabilize: false,
                //configurePhysics:true
        };

        network = new vis.Network(canvasDiv.get(0), data, options);
       // network.on('select', onSelect);
        network.on('doubleClick', doubleClick);
        network.selectNodes([objectId]);
        currentSelectedNode = objectId;
        displaySelectedNodeData(currentSelectedNode);
        
        animatedZoomExtent();
    }
    
    function buildNetworkDynamicLayout() {
        
        nodes.add({id: "fakeNode"});
        var data = {
                nodes: nodes,
                edges: edges
        };
        var options = {
                physics: {
                    barnesHut: {
                        gravitationalConstant: -4250, 
                        centralGravity: 0.05, 
                        springConstant: 0.002,
                        springLength: 500
                    }
                },
                nodes: {
                    shape: 'box', 
                    maxWidth: 200
                },
                edges: {
                    length: 500
                },
                stabilize: true,
                stabilizationIterations: 1500
                //configurePhysics:true
        };

        network = new vis.Network(canvasDiv.get(0), data, options);
        network.on('select', onSelect);
        network.on('doubleClick', doubleClick);
        network.selectNodes([objectId]);
        currentSelectedNode = objectId;
        displaySelectedNodeData(currentSelectedNode);
        
        network.on('stabilized', function () {
            canvasDiv.css('visibility', 'visible'); 
        });
        
        setTimeout(function () { 
            nodes.remove(["fakeNode"]); 
        }, 1);
        
        animatedZoomExtent();
    }    
    
    function animatedZoomExtent() {
        var intervalId = setInterval(function () { 
            network.zoomExtent();
        }, 1000/60);
        
        network.on('stabilized', function () {
            clearInterval(intervalId);
        });
        
        setTimeout(function () { 
            clearInterval(intervalId);
        }, 5000);
    }
    
    function onCloseClick() {
        APP.hideRelationshipsGraph();
    }
    
    function onGotRelationshipsSuccess(res, requestedLevel, zoomExtent) {
        addNewRelationshipsToGraph(res, requestedLevel, zoomExtent);
    
//***********Experimental work on searching remote repos for preview data        
//        var objectsToRetrieve = getRemoteObjectsToRetrieve(res);
//        if (objectsToRetrieve.length > 0) {
//            BatchObjectRetriever.retrieve(objectsToRetrieve, function (remoteResults) {
//                //augment the existing search results with the remote results
//                augmentResultsWithRemoteResults(res, remoteResults);
//                addNewRelationshipsToGraph(res, requestedLevel, zoomExtent);
//            }, function () {
//                console.log("error doing batch retrieve.");
//            });
//        } else {
//            addNewRelationshipsToGraph(res, requestedLevel, zoomExtent);
//        }
//************
    }
    
    function augmentResultsWithRemoteResults(res, remoteResults) {
        //TODO
    }
    
    function addNewRelationshipsToGraph(res, requestedLevel, zoomExtent) {
        var nodesToAdd = [];
        for (var i = 0; i < res.nodes.length; i++) {
            var node = res.nodes[i];
            if (existingNodes[node.id]) {
                continue;
            }
            existingNodes[node.id] = node;
            if (node.id === objectId) {
                node.level = 0;
            } else {
                node.level = requestedLevel;
            }
            
            nodesToAdd.push(node);
            if (node.id === objectId) {
                node.color = selectedColor;
            }
            var searchResult = res.results[node.id];
            node.searchResult = searchResult;
            if (searchResult != null) {
                if (searchResult.remoteRepository) {
                    //TODO //get remote data for node
                    addPreviewDataToNode(node, searchResult); 
                } else {
                    addPreviewDataToNode(node, searchResult); 
                }
            }
        }
        
        addLabelsToEdges(res.edges, res.results);
        
        var isEdgesAdded = false;
        if (network == null) {
            nodes = new vis.DataSet();
            edges = new vis.DataSet();
            if (res.nodes.length === 2) {
                setInitialPositionForNodes(res.nodes);
            }
            nodes.add(nodesToAdd);
            isEdgesAdded = addEdges(res.edges, requestedLevel);
            //buildNetwork();
            buildNetworkDynamicLayout();
        } else {
            nodes.add(nodesToAdd);
            isEdgesAdded = addEdges(res.edges, requestedLevel);
            if (zoomExtent) {
                animatedZoomExtent();
            }
        }
    }
    
    function getRemoteObjectsToRetrieve(res) {
        var objectsToRetrieve = [];
        for (var i = 0; i < res.nodes.length; i++) {
            var node = res.nodes[i];
            if (existingNodes[node.id]) {
                continue;
            }
            var searchResult = res.results[node.id];
            if (searchResult != null) {
                if (searchResult.remoteRepository) {
                    var objectToRetrieve = {
                            remoteRepository : searchResult.remoteRepository,
                            id : node.id
                    };
                    objectsToRetrieve.push(objectToRetrieve);
                }
            }
        }
        return objectsToRetrieve;
    }
    
    function doubleClick(properties) {
        var selectedNodes = properties.nodes;
        if (selectedNodes.length > 0) {
            var firstSelectedNodeId = selectedNodes[0];
            var retainGraph = true;
            APP.resolveHandle(firstSelectedNodeId, retainGraph);
        }
    } 
    
    function inboundToggleButtonClick() {
        inboundToggleButton.blur();
        if (outboundOnly) {
            inboundToggleButton.text("Restart with outbound links only");
            outboundOnly = false;
            //resetNetwork();
            resetNetworkByPrune();
        } else {
            inboundToggleButton.text("Restart with inbound links");
            outboundOnly = true;
            //resetNetwork();
            resetNetworkByPrune();
        }
    }
    
    function resetNetworkByPrune() {
        pruneBackToLevel1OutboundOnly();
               
        if (outboundOnly) {
            //you are done
        } else {
            //get relationships for the root node 
            var requestedLevel = 1;
            APP.getRelationships(objectId,  function (response) { onGotRelationshipsSuccess(response, requestedLevel); } , onGotRelationshipsError, outboundOnly);
        } 
    }
    
    
    function deleteLastAddedItems() {
        undoFanOutButton.blur();
        var currentTopLevel = getCurrentTopLevel();
        if (currentTopLevel === 1) return;
        
        var nodeIds = nodes.getIds();
        var nodesToDelete = [];
        
        for (var n = 0; n < nodeIds.length; n++) {
            var nodeId = nodeIds[n];
            var node = nodes.get(nodeId);
            if (node.level === currentTopLevel) {
                nodesToDelete.push(node);
            }
        }
        
        var linkIds = edges.getIds();
        var linksToDelete = [];
        
        for (var l = 0; l < linkIds.length; l++) {
            var linkId = linkIds[l];
            var link = edges.get(linkId);
            if (link.level === currentTopLevel) {
                linksToDelete.push(link);
            }
        }
        
        removeNodes(nodesToDelete);
        removeLinks(linksToDelete);
        
        //currentTopLevel = currentTopLevel -1;
    }
    
    //get the level 0 node
    //find its outbound links
    //find the nodes those links point at.
    //delete all nodes and links not in the above
    function pruneBackToLevel1OutboundOnly() {
//        currentTopLevel = 1;
        var rootObjectId = objectId;
        var rootNode = nodes.get(rootObjectId);
        var nodesToKeep = [];
        var linksToDelete = [];
        
        nodesToKeep.push(rootNode);
        
        var linkIds = edges.getIds();
        
        for (var i = 0; i < linkIds.length; i++) {
            var linkId = linkIds[i];
            var link = edges.get(linkId);
            if (link.from === rootObjectId) {
                var nodeToKeep = nodes.get(link.to);
                nodesToKeep.push(nodeToKeep);
            } else {
                linksToDelete.push(link);
            }
        }
        
        var nodesToDelete = [];
        
        var nodeIds = nodes.getIds();
        for (var n = 0; n < nodeIds.length; n++) {
            var nodeId = nodeIds[n];
            var node = nodes.get(nodeId);
            if (!isKeepNode(node, nodesToKeep)) {
                nodesToDelete.push(node);
            }
        }
        
        removeNodes(nodesToDelete);
        removeLinks(linksToDelete); 
    }
    
    function isKeepNode(node, nodesToKeep) {
        for (var n = 0; n < nodesToKeep.length; n++) {
            var nodeToKeep = nodesToKeep[n];
            if (node.id === nodeToKeep.id) {
                return true;
            }
        }
        return false;
    }
    
    function resetNetwork() {
//        currentTopLevel = 1;
        nodes.clear();
        edges.clear();
        existingEdges = {};
        existingNodes = {};
        var requestedLevel = 1;
        var zoomExtent = true;
        APP.getRelationships(objectId,  function (response) { onGotRelationshipsSuccess(response, requestedLevel, zoomExtent); } , onGotRelationshipsError, outboundOnly);
    }
    
    function setNewTargetObject(objectIdParam) {
        nodes.clear();
        edges.clear();
        nodes = new vis.DataSet();
        edges = new vis.DataSet();
        network = null;
        canvasDiv.css('visibility', 'hidden');
        
        objectId = objectIdParam;
        inboundToggleButton.text("Restart with inbound links");
        outboundOnly = true;
//        currentTopLevel = 1;

        existingEdges = {};
        existingNodes = {};
        var requestedLevel = 1;
        var zoomExtent = true;
        addReferrersLinkIfNeeded();
        APP.getRelationships(objectId,  function (response) { onGotRelationshipsSuccess(response, requestedLevel, zoomExtent); } , onGotRelationshipsError, outboundOnly);
    }
    self.setNewTargetObject = setNewTargetObject;
    
    function removeLinks(links) {
        edges.remove(links);
        for (var i = 0; i < links.length; i++) {
            var link = links[i];
            delete link.id;
            delete link.level; // level not part of edgeName
            var edgeName = JSON.stringify(link);
            delete existingEdges[edgeName];
        }  
    }
    
    function removeNodes(nodesToDelete) {
        nodes.remove(nodesToDelete);
        for (var i = 0; i < nodesToDelete.length; i++) {
            var node = nodesToDelete[i];
            if (node.id === currentSelectedNode) currentSelectedNode = null; 
            delete existingNodes[node.id];
        }
    } 
    
    function isLinkInbound(link) {
        var fromNode = existingNodes[link.from];
        var toNode = existingNodes[link.to];
        if (toNode.level < fromNode.level) {
            return true;
        } else {
            return false;
        }
    }
    
    function setInitialPositionForNodes(nodes) {
        nodes[0].y = 200;
        nodes[0].allowedToMoveY = true;
        
        nodes[1].y = 600;
        nodes[1].allowedToMoveY = true;
    }
    
    function addLabelsToEdges(edges, searchResultsMap) {
        for (var i = 0; i < edges.length; i++) {
            var edge = edges[i];
            var fromId = edge.from;
            var fromSearchResult = searchResultsMap[fromId];
            if (fromSearchResult != null) {
                addLabelToEdge(edge, fromSearchResult);
            }
        }
    }
    
    function addLabelToEdge(edge, fromSearchResult) {
        var jsonPointer = edge.jsonPointer;
        var schema = APP.getSchema(fromSearchResult.type, fromSearchResult.remoteRepository);
        var pointerToSchemaMap = SchemaExtractorFactory.get().extract(fromSearchResult.json, schema);
        var subSchema = pointerToSchemaMap[jsonPointer];
        if (subSchema === undefined) return;
        var handleReferenceNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, 'type', 'handleReference');
        if (handleReferenceNode === undefined) return;
        var handleReferenceType = handleReferenceNode['types'];
        if (!handleReferenceType) return;
        var idPointedToByReference = JsonUtil.getJsonAtPointer(fromSearchResult.json, jsonPointer);
        if (idPointedToByReference !== edge.to) return;

        var handleReferenceName = handleReferenceNode['name'];
        if (!handleReferenceName) {
            edge.label = jsonPointer;
        } else {
            if (startsWith(handleReferenceName, "{{") && endsWith(handleReferenceName, "}}")) {
                var expression = handleReferenceName.substr(2, handleReferenceName.length -4);
                var label = getValueForExpression(jsonPointer, expression, fromSearchResult.json);
                if (label !== "" && label !== null) {
                    edge.label = label;
                }
            } else {
                edge.label = handleReferenceName;
            }
        }
    }
    
    function getValueForExpression(jsonPointer, expression, jsonObject) {
        var result = "";
        var segments = jsonPointer.split('/').slice(1);
        if (startsWith(expression, "/")) {
            //treat the expression as a jsonPointer starting at the root
            result = JsonUtil.getJsonAtPointer(jsonObject, expression);
        } else if (startsWith(expression, "..")) {
            var segmentsFromRelativeExpression = expression.split('/').slice(1);
            segments.pop();
            var combinedSegments = segments.concat(segmentsFromRelativeExpression);
            var jsonPointerFromExpression = getJsonPointerFromSegments(combinedSegments);
            result = JsonUtil.getJsonAtPointer(jsonObject, jsonPointerFromExpression);
        } else {
           //consider the expression to be a jsonPointer starting at the current jsonPointer
            var targetPointer = jsonPointer + "/" + expression;
            result = JsonUtil.getJsonAtPointer(jsonObject, targetPointer);
        }
        if (typeof result !== 'string') {
            result = JSON.stringify(result);
        } 
        return result;
    }
    
    function getJsonPointerFromSegments(segments) {
        var jsonPointer = "";
        for (var i = 0; i < segments.length; i++) {
            var segment = segments[i];
            var encodedSegment = JsonUtil.encodeJsonPointerSegment(segment);
            jsonPointer = jsonPointer + "/" + encodedSegment;
        }
        return jsonPointer;
    }
    
    function addPreviewDataToNode(node, searchResult) {
        var nodeId = node.id;
        if (nodeId.length > 30) {
            nodeId = nodeId.substring(0, 30) + "...";
        }
        node.label = nodeId;
        
//        node.label += "\n" + "Level" + ": " + node.level;
        
        //if (!searchResult.remoteRepository) {
            var previewData = ObjectPreviewUtil.getPreviewData(searchResult, searchResult.remoteRepository);
            for (var jsonPointer in previewData) {
                var thisPreviewData = previewData[jsonPointer];
                if (thisPreviewData.isPrimary) {
                    var prettifiedPreviewData = ObjectPreviewUtil.prettifyPreviewJson(thisPreviewData.previewJson, maxPreviewStringLength);
                    if (!prettifiedPreviewData) continue;
                    node.label += "\n" + thisPreviewData.title + ": " + prettifiedPreviewData;
                }
            } 

            //If there are multiple schemas in the registrar include the type 
            if (APP.getSchemaCount() > 1) {
                var schema = APP.getSchema(searchResult.type);
                var typeTitle = searchResult.type;
                if (schema && schema.title) {
                    typeTitle = schema.title;
                }
                if (typeTitle) node.label += "\n" + "Type" + ": " + typeTitle;
            }
//        } else {
//            node.label += "\n" + "Located remotely";
//        }
          if (searchResult.remoteRepository) {
              node.label += "\n" + "Located remotely";
          }
    }
    
    function addEdges(edgesToAdd, requestedLevel) {
        var added = false;
        for (var i = 0; i < edgesToAdd.length; i++) {
            var edge = edgesToAdd[i];
            var edgeName = JSON.stringify(edge);
            if (existingEdges[edgeName] === true) {
                //we already have this edge don't add it
            } else {
                existingEdges[edgeName] = true;
                edge.level = requestedLevel;
                edges.add(edge);
                added = true;
            }
        }
        return added;
    }
    
    function onFanOutAllClick() {
        var requestedLevel = getCurrentTopLevel() + 1;
        for (var nodeId in existingNodes) {
            APP.getRelationships(nodeId, function (response) { onGotRelationshipsSuccess(response, requestedLevel); }, onGotRelationshipsError, outboundOnly);
        }
    }
    
    function onSelect(properties) {
        var selectedNodes = properties.nodes;
        if (selectedNodes.length > 0) {
            var firstSelectedNode = selectedNodes[0];
            currentSelectedNode = firstSelectedNode;
            displaySelectedNodeData(currentSelectedNode);
        } else {
            selectedDetails.empty().append($('<ul class="graph-selected-node-preview"></ul>'));
        }
    }
    
    function displaySelectedNodeData(selectedNodeId) {
        var node = existingNodes[selectedNodeId];
        var searchResult = node.searchResult;
        var previewData = ObjectPreviewUtil.getPreviewData(searchResult);
        var ul = $('<ul class="graph-selected-node-preview"></ul>');
        var placedId = false;
        for (var jsonPointer in previewData) {
            var thisPreviewData = previewData[jsonPointer];
            var prettifiedPreviewData = ObjectPreviewUtil.prettifyPreviewJson(thisPreviewData.previewJson);
            if (!prettifiedPreviewData) continue;
            var nodeDetails = $('<li/>');
            if (thisPreviewData.isPrimary) {
                var b = $('<b/>');
                nodeDetails.append(b);
                nodeDetails = b;
            }
            if (thisPreviewData.excludeTitle) {
                nodeDetails.text(prettifiedPreviewData);
            } else {
                nodeDetails.text(thisPreviewData.title + ": " + prettifiedPreviewData);
            }
            if (thisPreviewData.isPrimary && !placedId) {
                ul.prepend($('<li/>').text("Id: " + searchResult.id));
                ul.prepend(nodeDetails);
                placedId = true;
            } else {
                ul.append(nodeDetails);
            }
        } 
        if (!placedId) {
            ul.prepend($('<li/>').append($('<b/>').text("Id: " + searchResult.id)));
        }
        //ul.prepend($('<li/>').append($('<b/>').text("level: " + node.level)));
        selectedDetails.empty().append(ul);
        onResize(false);
    }
    
    function onFanOutSelectedClick() {
        fanOutSelectedButton.blur();
        if (currentSelectedNode != null) {
//            currentTopLevel = currentTopLevel +1;
            var requestedLevel = getCurrentTopLevel() + 1;
            APP.getRelationships(currentSelectedNode, function (response) { onGotRelationshipsSuccess(response, requestedLevel); }, onGotRelationshipsError, outboundOnly);
        }
   }
    
    function onResizeClick() {
        // prevent button from staying grey after the resize
        resizeButton.blur().hide().show(0);
        if (isBig) {
            containerDiv.removeClass('cnri-big-modal');
            $('body').removeClass('modal-open');
            resizeButton.empty().append('<span class="glyphicon glyphicon-resize-full"></span>');
            canvasDiv.height(770);
            isBig = false;
        } else {
            containerDiv.addClass('cnri-big-modal');
            $('body').addClass('modal-open');
            resizeButton.empty().append('<span class="glyphicon glyphicon-resize-small"></span>');
            isBig = true;
        }
        onResize(true);
        animatedZoomExtent();
    }
    
    function onResize(force) {
        if (isBig) {
            canvasDiv.height(containerDiv.height() - selectedDetails.outerHeight(true) - 30);
        }
        if (isBig || force) {
            network.setSize(canvasDiv.width(), canvasDiv.height());
            network.redraw();
        }
    }
    self.onResize = onResize;
    
    function onGotRelationshipsError(res) {
        console.log(res);
    }    
    
    function startsWith(str, prefix) {
        return str.lastIndexOf(prefix, 0) === 0;
    }
    
    function endsWith(str, suffix) {
        return str.indexOf(suffix, str.length - suffix.length) !== -1;
    }
    
    constructor();
}

window.RelationshipsGraph = RelationshipsGraph;

})();