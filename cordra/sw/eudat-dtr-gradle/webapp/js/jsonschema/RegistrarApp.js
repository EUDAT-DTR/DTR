/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";

var window = window || self;

function RegistrarApp() {
    var self = this;
    var schemas = null;
    var uiConfig = null;
    var serverPrefix = null;
    var toolBar = null;
    var notifications = null;
    var searchWidget = null;
    var objectId = null;
    var editor = null;
        
    var toolBarDiv = null;
    var editorDiv = null;
    var searchDiv = null;
    var searchResultsDiv = null;
    var notificationsDiv = null;
    var relationshipsGraphDiv = null;
    var htmlContentDiv = null;
    
    var relationshipsGraph = null;
    var searchResultsHidden = false;
    var shownInitialFragment = false;
    
    var navBar = null;
    var authWidget = null;
    
    var lastFragment = null;
    var lastProgrammaticallySetFragment = null;

    var remoteRepositories = {}; // map from uri to type to schema, these are schemas stored in remote registars that are referenced by objects stored locally
    var alienSchemas = {}; // map from uri to type to schema, these are schemas that have been replicated from a remote registrar into the local registrar
    
    var authConfig;
    
    var subscriptionBanner = null;
    
    function constructor() {
        editorDiv = $('#editor');
        searchDiv = $("#search");
        searchResultsDiv = $("#search-results");
        notificationsDiv = $("#notifications");
        relationshipsGraphDiv = $("#relationships");
        htmlContentDiv = $("#htmlContent");

        $.ajax({
            url: "initData",
            beforeSend: function( xhr ) {
                xhr.overrideMimeType( "application/json" );
            },
            dataType: 'json'
        }).done(onGotInitData);

        $(window).resize(onResize);
    }
    
    function onResize() {
        if (relationshipsGraph) {
            relationshipsGraph.onResize();
        }
    }
    
    function onGotInitData(response) {
        uiConfig = response.design.uiConfig;
        schemas = response.design.schemas;
        authConfig = response.design.authConfig;
        if (response.design.alienSchemas) {
            alienSchemas = response.design.alienSchemas;
        }
        serverPrefix = response.design.serverPrefix;
        subscriptionBanner = new SubscriptionBanner($("#subscriptionBanner"), serverPrefix);
        
        
        //$('.navbar-brand').text(uiConfig.title);
        $('title').text(uiConfig.title);
        
        var navBarElement = $("#navBar");
        navBar = new NavBar(navBarElement, uiConfig.navBarLinks, schemas, alienSchemas);
        
        notifications = new Notifications(notificationsDiv);
        self.notifications = notifications;
        searchWidget = new SearchWidget(searchDiv, searchResultsDiv, schemas, serverPrefix, false);

        authWidget = new AuthenticatorWidget($("#authenticateDiv"), onAuthenticationStateChange, response.isActiveSession, response.username, response.userId);
        onAuthenticationStateChange();
        
        // At this point, the editor and objectId are still null, so onAuthenticationStateChange will call handleNewWindowLocation for us
        // handleNewWindowLocation();
        //window.onpopstate = handleOnpopstate;
        window.onhashchange = handleOnhashchange;

        retrieveRemoteSchemas();
    }
    
    function handleOnpopstate() {
        handleNewWindowLocation();
    }
    
    function handleOnhashchange() {
        handleNewWindowLocation();
    }
    
    //looks through the schemas to see if there are any that contain a pointer to object on other registrars. 
    //GETs the schemas for the remot eobject pointed at.
    function retrieveRemoteSchemas() { 
        remoteRepositories = getReferencedRemoteRepositories();
        for (var remoteRepositoryUri in remoteRepositories) {
            $.getJSON(remoteRepositoryUri+"design").done(function (response) { onGotRemoteDesign(response, remoteRepositoryUri); });
        }
    }
    
    function onGotRemoteDesign(response, remoteRepositoryUri) {
        var remoteSchemas = response.schemas;
        remoteRepositories[remoteRepositoryUri] = remoteSchemas;
    }
    
    function getReferencedRemoteRepositories() {
        var remoteRepositoriesMap = {};
        for(var schemaName in schemas) {
            var schema = schemas[schemaName];
            var cnriProperties = JsonUtil.getPropertiesNamed(Constants.REPOSITORY_SCHEMA_KEYWORD, schema);
            for (var i = 0; i < cnriProperties.length; i++) {
                var cnriProperty = cnriProperties[i];
                var remoteRepository = JsonUtil.getDeepProperty(cnriProperty, 'type', 'handleReference', 'remoteRepository');
                if (remoteRepository) {
                    remoteRepositoriesMap[remoteRepository] = true;
                }
            }
        }
        return remoteRepositoriesMap;
    }
    
    function handleNewWindowLocation() {
        var fragment = window.location.hash.substr(1);
        if (lastProgrammaticallySetFragment) {
            if (lastProgrammaticallySetFragment === fragment) {
                lastProgrammaticallySetFragment = null;
                return; //Fragment was set on the client side by code and has already been handled.
            }
        } 
        
        if (fragment != null && fragment != "") {
            shownInitialFragment = true;
            if (startsWith(fragment, "objects/")) {
                if (startsWith(fragment, "objects/?query=")) {
                    var fragmentQuery = getQueryFromFragment(fragment);
                    performSearchWidgetSearch(fragmentQuery);
                } else {
                    var fragmentObjectId = getObjectIdFromFragment(fragment);
                    if (fragmentObjectId != null && fragmentObjectId !== "") {
                        if (fragmentObjectId !== objectId) {
                            resolveHandle(fragmentObjectId);
                        }
                    }
                }
            } else if (startsWith(fragment, "pages/")) {
                var pageId = getPageIdFromFragment(fragment);
                if (pageId != null && pageId !== "") {
                    var options = {
                            type : "document",
                            elementName : pageId
                    };
                    showHtmlPageFor(options);
                }
            } else if (startsWith(fragment, "urls/")) {
                var url = getUrlFromFragment(fragment);
                if (url != null && url !== "") {
                    var options = {
                            type : "url",
                            url : url
                    };
                    showHtmlPageFor(options);
                }
            }
        } else if (objectId != null) {
            var showSearch = true;
            hideObjectEditor(showSearch);
        } else if (!shownInitialFragment && uiConfig.initialFragment) {
            shownInitialFragment = true;
            window.location.hash = uiConfig.initialFragment;
        } 
    }
    
    function showHtmlPageFor(options) {
        hideRelationshipsGraph();
        objectId = null;
        if (editor) editor.destroy(); 
        editorDiv.empty();
        editorDiv.hide();
        if (searchWidget != null) {
            searchWidget.hideResults();
            searchResultsHidden = true;
        }       
        
        htmlContentDiv.show();
        var htmlPageViewer = new HtmlPageViewer(htmlContentDiv, options);
    }
    
    function getUiConfig() {
        return uiConfig;
    }
    self.getUiConfig = getUiConfig;
    
    function getServerPrefix() {
        return serverPrefix;
    }
    self.getServerPrefix = getServerPrefix;
    
    function getSchema(type, remoteRepository) {
        if (type === "Schema") {
            return schemas["Schema"];
        }
        if (remoteRepository) {
            var remoteSchemas = alienSchemas[remoteRepository];
            if (remoteSchemas) {
                return remoteSchemas[type];
            } 
            remoteSchemas = remoteRepositories[remoteRepository];
            if (remoteSchemas) {
                return remoteSchemas[type];
            } else {
                return undefined;
            }
        } else {
            return schemas[type];
        }
    }
    self.getSchema = getSchema;
    
    function getSchemaCount() {
        return Object.keys(schemas).length;
    }
    self.getSchemaCount = getSchemaCount;
    
    function createNewObject(type) {
        searchResultsHidden = false;
        editorDiv.empty();
        clearFragment();
        hideRelationshipsGraph();
        objectId = null;
        var allowEdits = true;
        
        var contentPlusMeta = {
                id : null,
                type : type,
                content : {},
                metadata : {}
        };
        
        var options = {
                contentPlusMeta : contentPlusMeta,
                schema : schemas[type],
                type : type,
                objectJson : {},
                objectId : null,
                relationshipsButtonText : uiConfig.relationshipsButtonText,
                disabled : false,
                allowEdits : allowEdits,
                remoteRepository : null
        };
        if (editor) editor.destroy(); 
        editor = new ObjectEditor(editorDiv, options); 
        editorDiv.show();
    }
    self.createNewObject = createNewObject;
    
    function isUserAlowedToEdit() {
        //TODO consider checking check acls on object instead
        return authWidget.getIsActiveSession();
    }
    
    function resolveHandle(objectId, retainGraph) {
        $.ajax({
            url: 'objects/' + objectId + "?full",
            type: 'GET',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            onGotObject(res, status, xhr, retainGraph);
        }).fail(function (res, status, xhr) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
                notifications.alertError("Unauthorized");
            } else if (res.status === 403) {
                notifications.alertError("Forbidden");
            } 
            else {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
                console.log(res);
            }
        });
    }
    self.resolveHandle = resolveHandle;
    
    function getFileMetadata(objectId, jsonPointer, succeessCallback, failCallback) {
        $.ajax({
            url: 'objects/' + objectId + '?jsonPointer=' + encodeURIComponent(jsonPointer).replace(/%2F/g,"/") + "&metadata=true",
            type: 'GET',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            succeessCallback(res, status, xhr);
        }).fail(function (res, status, xhr) {
            if (failCallback) failCallback(res, status, xhr);
        });
    }
    self.getFileMetadata = getFileMetadata;
    
    //Just gets an object by id, does not start editing that object
    function getObjectLocally(objectId, successCallBack, errorCallback) {
        $.ajax({
            url: 'objects/' + objectId,
            type: 'GET',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            successCallBack(res, status, xhr);
        }).fail(function (res, status, xhr) {
            errorCallback(res, status, xhr);
        });
    } 
    self.getObjectLocally = getObjectLocally;
    
    function getObjectRemotely(remoteRepository, objectId, successCallBack, errorCallback) {
        if (remoteRepository.substring(remoteRepository.length-1) !== '/') remoteRepository += '/';
        $.ajax({
            url: remoteRepository + 'objects/' + objectId,
            type: 'GET'
        }).done(function (res, status, xhr) {
            successCallBack(res, status, xhr);
        }).fail(function (res, status, xhr) {
            errorCallback(res, status, xhr);
        });
    } 
    self.getObjectRemotely = getObjectRemotely;
    
    function getAclForCurrentObject(onGotAclSuccess)  {
        $.ajax({
            url: 'acls/' + objectId,
            type: 'GET',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            onGotAclSuccess(res, status, xhr);
        }).fail(function (res) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
                notifications.alertError("Unauthorized");
            } else if (res.status === 403) {
                notifications.alertError("Forbidden");
            } 
            else {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
                console.log(res);
            }
        });
    }
    self.getAclForCurrentObject = getAclForCurrentObject;
    
    function saveAclForCurrentObject(newAcl, onSuccess, onFail) {
        notifications.clear();
        $.ajax({
            url: 'acls/' + objectId,
            type: 'PUT',
            data: JSON.stringify(newAcl),
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("ACL for Object " + objectId + " saved.");
            onSuccess(res, status, xhr);
        }).fail(function (res, status, xhr) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
            } 
            onFail(res, status, xhr);
        });
    }
    self.saveAclForCurrentObject = saveAclForCurrentObject;
    
    function setToUnauthenticated() {
        authWidget.setUiToStateUnauthenticated();
    }
    self.setToUnauthenticated = setToUnauthenticated;
    
    function onGotObject(contentPlusMeta, status, xhr, retainGraph) {
        notifications.clear();
        if (searchWidget != null) {
            searchWidget.hideResults();
            searchResultsHidden = true;
        }
        editorDiv.empty();
        editorDiv.show();
        var location = xhr.getResponseHeader("Location");
        objectId = getObjectIdFromLocation(location);
        var type = xhr.getResponseHeader("X-Schema");
        var remoteRepository = xhr.getResponseHeader("Origin");
        var permission = xhr.getResponseHeader("X-Permission");
        
        setObjectIdInFragment(objectId);
        if (retainGraph) {
            relationshipsGraph.setNewTargetObject(objectId);
        } else {
            hideRelationshipsGraph();
        }
        hideHtmlContent();
        var allowEdits = false; 
        if (permission === "WRITE") {
            allowEdits = true;
        }
        
        var userId = authWidget.getCurrentUserId();
        var allowClone = isAllowedToCreate(userId, type);
        
        var schema = getSchema(type, remoteRepository);
        var content = contentPlusMeta.content;
        var options = {
                contentPlusMeta : contentPlusMeta,
                schema : schema,
                type : type,
                objectJson : content,
                objectId : objectId,
                relationshipsButtonText : uiConfig.relationshipsButtonText,
                disabled : true,
                allowEdits : allowEdits,
                remoteRepository : remoteRepository,
                allowClone : allowClone
        };
        if (editor) editor.destroy();
        editor = new ObjectEditor(editorDiv, options);
    }
    
    // This method does not fiddle the UI in any way; caller is response
    function search(query, pageNum, pageSize, sortFields, onSuccess, onError) {
        var url = "objects/?query=" + encodeURIComponent(query) + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        if (sortFields) {
            url = url + "&sortFields=" + sortFields;
        }
        
        $.getJSON(url)
        .done(function (res, status, xhr) {
//            notifications.clear();
            onSuccess(res, status, xhr);
        }).fail(function (xhr, status, error) {
//            var message = JSON.parse(res.responseText);
//            notifications.alertError(message.message);
            onError(xhr.responseJSON, status, xhr);
        });
    }
    self.search = search;
    
    function searchRemoteRepository(baseUri, query, pageNum, pageSize, sortFields, onSuccess, onError) {
        var url = baseUri + "objects/?query=" + encodeURIComponent(query) + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        if (sortFields) {
            url = url + "&sortFields=" + sortFields;
        }
        $.getJSON(url)
        .done(function (res, status, xhr) {
            notifications.clear();
            onSuccess(res, status, xhr);
        }).fail(function (res, status, xhr) {
            var message = null;
            if (res.responseText !== "" ) {
                message = JSON.parse(res.responseText);
            } else {
                message = {
                        message : "An unknown error occured"
                };
            }
            notifications.alertError(message.message);
            onError(res, status, xhr);
        });
    }
    self.searchRemoteRepository = searchRemoteRepository;
    
    function performSearchWidgetSearch(query, sortFields) {
        hideHtmlContent();
        setQueryInFragment(query);
        searchWidget.search(query, sortFields);
    }
    self.performSearchWidgetSearch = performSearchWidgetSearch;
    
    function hideObjectEditor(showSearch) {
        objectId = null;
        hideRelationshipsGraph();
        if (editor) editor.destroy(); 
        editorDiv.empty();
        editorDiv.hide();
        editor = null;
        clearFragment();
        if (showSearch && searchWidget != null) {
            if (searchResultsHidden) {
                searchResultsHidden = false;
                searchWidget.showResults();
            }
        }
    }
    self.hideObjectEditor = hideObjectEditor;
    
    function hideHtmlContent() {
        htmlContentDiv.hide();
    }
    self.hideHtmlContent = hideHtmlContent;
    
    function showRelationshipsGraph(objectId) {
        relationshipsGraphDiv.empty();
        relationshipsGraphDiv.show();
        if (relationshipsGraph != null) {
            //maybe need to clean up previous one?
        }
        relationshipsGraph = new RelationshipsGraph(relationshipsGraphDiv, objectId);
    }
    self.showRelationshipsGraph = showRelationshipsGraph;
    
    function hideRelationshipsGraph() {
        relationshipsGraphDiv.empty();
        relationshipsGraphDiv.hide();
        relationshipsGraph = null;
    }
    self.hideRelationshipsGraph = hideRelationshipsGraph;
    
    function getRelationships(objectId, successCallback, errorCallback, outboundOnly) {
        var url = 'relationships/' + objectId;
        if (outboundOnly) {
            url = url + "?outboundOnly=true";
        }
        $.getJSON(url).done(function (res, status, xhr) {
            successCallback(res, status, xhr);
        }).fail(function (res, status, xhr) {
            errorCallback(res, status, xhr);
        });
    }
    self.getRelationships = getRelationships;
    
    function deleteObject(objectId) {
        var dialog = new ModalYesNoDialog("Are you sure you want to delete this object?", function () {
            yesDeleteCallback(objectId);
        }, noDeleteCallback, self);
        dialog.show();
    }
    self.deleteObject = deleteObject;
    
    function yesDeleteCallback(objectId) {
        $.ajax({
            url: 'objects/' + objectId,
            type: 'DELETE',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res) {
            hideRelationshipsGraph();
            if (editor) editor.destroy(); 
            editorDiv.empty();
            editor = null;
            clearFragment();
            notifications.alertSuccess("Object " + objectId + " deleted.");
        }).fail(function (res, status, xhr) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
            }
            var message = JSON.parse(res.responseText);
            notifications.alertError(message.message);
            console.log(res);
        });
    }
    
    function noDeleteCallback() {
        //no-op
    }
    
    function cloneCurrentObject() {
        //not sure if we should allow cloning of object that came from a remote registrar
        
        var currentObjectJson = editor.getJsonFromEditor();
        var currentObjectType = editor.getType();
        var newObject = ObjectCloner.clone(currentObjectJson, currentObjectType, schemas[currentObjectType]);
        
        searchResultsHidden = false;
        
        editorDiv.empty();
        clearFragment();
        hideRelationshipsGraph();
        objectId = null;
        //var allowEdits = isUserAlowedToEdit();
        
        var contentPlusMeta = {
                id : null,
                type : currentObjectType,
                content : newObject,
                metadata : {}
        };
        
        var options = {
                contentPlusMeta : contentPlusMeta,
                schema : schemas[currentObjectType],
                type : currentObjectType,
                objectJson : newObject,
                objectId : null,
                relationshipsButtonText : uiConfig.relationshipsButtonText,
                disabled : false,
                allowEdits : true,
                remoteRepository : null
        };
        if (editor) editor.destroy(); 
        editor = new ObjectEditor(editorDiv, options);   
        editorDiv.show();
    }
    self.cloneCurrentObject = cloneCurrentObject;
    
    function saveObject(formData, objectId, errorCallback) {
        notifications.clear();
        $.ajax({
            url: 'objects/' + objectId + "?full",
            type: 'PUT',
            data: formData,
            contentType: false, // force jQuery to let the browser set it
            processData: false, // force jQuery to leave the FormData alone
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            editorDiv.empty();
            var location = xhr.getResponseHeader("Location");
            objectId = getObjectIdFromLocation(location);
            var type = xhr.getResponseHeader("X-Schema");
            var permission = xhr.getResponseHeader("X-Permission");
            var remoteRepository = null;
            setObjectIdInFragment(objectId);
            //var jsonObject = res;
            
            var allowEdits = false; 
            if (permission === "WRITE") {
                allowEdits = true;
            }
            
            var userId = authWidget.getCurrentUserId();
            var allowClone = isAllowedToCreate(userId, type);
            
            var content = res.content;
            var options = {
                    contentPlusMeta : res,
                    schema : schemas[type],
                    type : type,
                    objectJson : content,
                    objectId : objectId,
                    relationshipsButtonText : uiConfig.relationshipsButtonText,
                    disabled : true,
                    allowEdits : allowEdits,
                    remoteRepository : remoteRepository,
                    allowClone : allowClone
            };
            if (editor) editor.destroy(); 
            editor = new ObjectEditor(editorDiv, options);
            notifications.alertSuccess("Object " + objectId + " saved.");
        }).fail(function (res, status, xhr) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
            }
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving this object.");
            }
            if (errorCallback) errorCallback();
            console.log(res);
        });
    }
    self.saveObject = saveObject;
    
    function setObjectIdInFragment(objectId) {
        var fragment = "objects/" + objectId;
        lastProgrammaticallySetFragment = fragment;
        window.location.hash = fragment;
    }
    
    function setQueryInFragment(query) {
        var fragment = "objects/?query=" + query;
        lastProgrammaticallySetFragment = fragment;
        window.location.hash = fragment;
    }
    self.setQueryInFragment = setQueryInFragment;
    
    function clearFragment() {
        window.location.hash = "";
    }
    
    function publishVersion(objectId, onSuccess, onFail) {
        notifications.clear();
        var url = 'versions/?objectId=' + objectId;
        $.ajax({
            url: url,
            type: 'POST',
            contentType: false, // force jQuery to let the browser set it
            processData: false, // force jQuery to leave the FormData alone
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("Version published with id " + res.id);
            if (onSuccess) {
                onSuccess(res);
            }
        }).fail(function (res, status, xhr) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
                notifications.alertError("Unauthorized");
            } else if (res.status === 403) {
                notifications.alertError("Forbidden");
            } else {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
                console.log(res);
            }
            if (onFail) {
                onFail();
            }
        });
    }
    self.publishVersion = publishVersion;
    
    function getVersionsFor(objectId, onSuccess, onFail) {
        $.ajax({
            url: 'versions/?objectId=' + objectId,
            type: 'GET',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            if (onSuccess) {
                onSuccess(res);
            }
        }).fail(function (res, status, xhr) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
                notifications.alertError("Unauthorized");
            } else if (res.status === 403) {
                notifications.alertError("Forbidden");
            } 
            else {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
                console.log(res);
            }
            if (onFail) {
                onFail();
            }
        });
    }
    self.getVersionsFor = getVersionsFor;
    
    function createObject(formData, type, suffix, errorCallback) {
        notifications.clear();
        var url = 'objects/?type=' + type + "&full";
        if (suffix) {
            url = url + "&suffix=" + suffix;
        }
        $.ajax({
            url: url,
            type: 'POST',
            data: formData,
            contentType: false, // force jQuery to let the browser set it
            processData: false, // force jQuery to leave the FormData alone
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            editorDiv.empty();
            var location = xhr.getResponseHeader("Location");
            objectId = getObjectIdFromLocation(location);
            var type = xhr.getResponseHeader("X-Schema");
            var permission = xhr.getResponseHeader("X-Permission");
            setObjectIdInFragment(objectId);
            //var jsonObject = res;
            
            var content = res.content;
            var options = {
                    contentPlusMeta : res,
                    schema : schemas[type],
                    type : type,
                    objectJson : content,
                    objectId : objectId,
                    relationshipsButtonText : uiConfig.relationshipsButtonText,
                    disabled : true,
                    allowEdits : true,
                    remoteRepository : null
            };
            if (editor) editor.destroy(); 
            editor = new ObjectEditor(editorDiv, options);
            notifications.alertSuccess("Object " + objectId + " saved.");
        }).fail(function (res, status, xhr) {
            if (res.status === 401) {
                authWidget.setUiToStateUnauthenticated();
                notifications.alertError("Unauthorized");
            } else if (res.status === 403) {
                notifications.alertError("Forbidden");
            } else {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
                console.log(res);
            }
            if (errorCallback) errorCallback();
        });
    }
    self.createObject = createObject;
    
    function editCurrentObject() {
        editorDiv.empty();
        editorDiv.show();
        var type = editor.getType();
        var jsonObject = editor.getJsonFromEditor();
        
        var contentPlusMeta = editor.getContentPlusMeta();
        var remoteRepository = editor.getRemoteRepository();
        var options = {
                contentPlusMeta : contentPlusMeta,
                schema : schemas[type],
                type : type,
                objectJson : jsonObject,
                objectId : objectId,
                relationshipsButtonText : uiConfig.relationshipsButtonText,
                disabled : false,
                allowEdits : true,
                remoteRepository : remoteRepository
        };
        if (editor) editor.destroy(); 
        editor = new ObjectEditor(editorDiv, options);
    }
    self.editCurrentObject = editCurrentObject;
    
    function onAuthenticationStateChange() {

        //console.log('onAuthenticationStateChange');

        var userId = authWidget.getCurrentUserId();
        if (authConfig) {
            var typesCreate = getTypesUserCanCreate(userId);
            var typesRead = getTypesUserCanRead(userId);

            searchWidget.setAllowCreateTypes(typesCreate);
            searchWidget.setAllowSearchTypes(typesRead);
        }
        if (editor != null && objectId != null) {
            var currentObjectId = objectId;
            var showSearch = false;
            hideObjectEditor(showSearch);
            resolveHandle(currentObjectId, false);
        } else {
            handleNewWindowLocation();
        }
    }
    
    function getTypesUserCanCreate(userId) {
        var result = [];
        for (var type in schemas) {
            if (isAllowedToCreate(userId, type)) {
                result.push(type);
            }
        }
        //console.log('    getTypesUserCanCreate = [' + result + ']');
        return result;
    }

    function isAllowedToCreate(userId, type) {
        if (userId === "admin") return true;        
        var acl = null;
        var schemaAcl = authConfig.schemaAcls[type];
        if (schemaAcl && schemaAcl.aclCreate) {
            acl = schemaAcl.aclCreate;
        } else {
            if (authConfig.defaultAcls) {
                acl = authConfig.defaultAcls.aclCreate;
            }
        }
        if (!acl) return false;
        for (var i = 0; i < acl.length; i++) {
            var permittedId = acl[i];
            if ("public" === permittedId) return true;
            if (userId != null && "authenticated" === permittedId) return true;
            if (userId === permittedId) return true;
        }
        return false;
    }

    function getTypesUserCanRead(userId) {
        var result = [];
        for (var type in schemas) {
            if (isAllowedToRead(userId, type)) {
                result.push(type);
            }
        }
        //console.log('    getTypesUserCanRead = [' + result + ']');
        return result;
    }
    
    function isAllowedToRead(userId, type) {
        if (userId === "admin") return true;        
        var acl = null;
        var schemaAcl = authConfig.schemaAcls[type];
        if (schemaAcl && schemaAcl.defaultAclRead) {
            acl = schemaAcl.defaultAclRead;
        } else {
            if (authConfig.defaultAcls) {
                acl = authConfig.defaultAcls.defaultAclRead;
            }
        }
        if (!acl) return false;
        for (var i = 0; i < acl.length; i++) {
            var permittedId = acl[i];
            if ("public" === permittedId) return true;
            if (userId != null && "authenticated" === permittedId) return true;
            if (userId === permittedId) return true;
        }
        return false;
    }
    
    function getObjectId() {
        return objectId;
    }
    self.getObjectId = getObjectId;
    
    function getObjectIdFromLocation(location) {
        var path = "/objects/";
        return location.substring(path.length);
    }
    
    function getObjectIdFromFragment(fragment) {
        var path = "objects/";
        return fragment.substring(path.length);
    }
    
    function getQueryFromFragment(fragment) {
        var prefix = "objects/?query=";
        return decodeURIComponent(fragment.substring(prefix.length));
    }
    
    function getPageIdFromFragment(fragment) {
        var path = "pages/";
        return fragment.substring(path.length);
    }
    
    function getUrlFromFragment(fragment) {
        var path = "urls/";
        return fragment.substring(path.length);
    }
    
    function getCsrfCookieToken() {
        return $.cookie("Csrf-token");
    }
    self.getCsrfCookieToken = getCsrfCookieToken;
    
    function startsWith(str, prefix) {
        return str.lastIndexOf(prefix, 0) === 0;
    }

    constructor();
}

window.RegistrarApp = RegistrarApp;

})();
