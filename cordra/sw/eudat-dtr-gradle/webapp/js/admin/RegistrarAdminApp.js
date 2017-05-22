/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

(function(){
"use strict";
    
function RegistrarAdminApp() {
    var self = this;
    var notifications = null;
    var notificationsDiv = null;
    var schemaEditorDiv = null;
    var schemaEditor = null;
    var design = null;
    var schemasDiv = null;
    var schemasToolBarDiv = null;
    var schemasToolBar = null;
    var authWidget = null;
    var uiConfigEditor = null;
    var uiConfigDiv = null;
    var replicationDiv = null;
    var replicationEditor = null;
//    var remoteRepositoriesDiv = null;
//    var remoteRepositoriesEditor = null;
//    var replicationCredentialsDiv = null;
//    var replicationCredentialsEditor = null;
    var authConfigDiv = null;
    var authConfigEditor = null;
    var handleMintingConfigDiv = null;
    var handleMintingConfigEditor = null;
    var dashboardDiv = null;
    var dashboard = null;
    var networkAndSecurityDiv = null;
    var networkAndSecurity = null
    
    var sections = {};
    
    function constructor() {
        notificationsDiv = $("#notifications");
        schemasDiv = $("#schemas");
        schemasToolBarDiv = $("#schemasToolBar");
        schemaEditorDiv = $("#schemaEditor");
        uiConfigDiv = $("#ui");
        replicationDiv = $("#replication");
//        remoteRepositoriesDiv = $("#replication");
//        replicationCredentialsDiv = $("#replicationCredentials");
        authConfigDiv = $("#authConfig");
        handleMintingConfigDiv = $("#handleRecords");
        dashboardDiv = $("#dashboard");
        networkAndSecurityDiv = $("#networkAndSecurity");
 
        sections["schemas"] = schemasDiv;
        sections["ui"] = uiConfigDiv;
//        sections["replication"] = remoteRepositoriesDiv;
        sections["replication"] = replicationDiv;
//        sections["replicationCredentials"] = replicationCredentialsDiv;
        sections["authConfig"] = authConfigDiv;
        sections["handleRecords"] = handleMintingConfigDiv;
        sections["dashboard"] = dashboardDiv;
        sections["networkAndSecurity"] = networkAndSecurityDiv;
        
        notifications = new Notifications(notificationsDiv);
        self.notifications = notifications;
        $.getJSON("initData").done(onGotInitData);
    }
    
    // meeting the interface from the Registrar APP
    function getObjectId() {
        return null;
    }
    self.getObjectId = getObjectId;
    
    function getCsrfCookieToken() {
        return $.cookie("Csrf-token");
    }
    self.getCsrfCookieToken = getCsrfCookieToken;
    
    function onGotInitData(response) {
        authWidget = new AuthenticatorWidget($("#authenticateDiv"), onAuthenticationStateChange, response.isActiveSession, response.username, response.userId);
        design = response.design;
        schemasToolBar = new SchemasToolBar(schemasToolBarDiv, design.schemas);
        var firstSchema = getFirstSchemaAndType(design.schemas);
        if (firstSchema) {
            schemaEditor = new SchemaEditor(schemaEditorDiv, firstSchema.schema, firstSchema.type);
            schemaEditorDiv.show();
        }
        uiConfigEditor = new UiConfigEditor(uiConfigDiv, response.design.uiConfig);
        
        replicationEditor = new ReplicationEditor(replicationDiv, response.design.remoteRepositories, response.design.replicationCredentials);
//        remoteRepositoriesEditor = new RemoteRepositoriesEditor(remoteRepositoriesDiv, response.design.remoteRepositories);
//        replicationCredentialsEditor = new ReplicationCredentialsEditor(replicationCredentialsDiv, response.design.replicationCredentials);
        authConfigEditor = new AuthConfigEditor(authConfigDiv, response.design.authConfig);
        handleMintingConfigEditor = new HandleMintingConfigEditor(handleMintingConfigDiv, response.design.handleMintingConfig);
        dashboard = new Dashboard(dashboardDiv);
        networkAndSecurity = new NetworkAndSecurity(networkAndSecurityDiv, response.isActiveSession, response.userId);
        //window.onpopstate = handleNewWindowLocation;
        window.onhashchange = handleNewWindowLocation;
        
        var fragment = window.location.hash.substr(1);
        if (fragment != null && fragment != "") {
            handleNewWindowLocation();
        } else {
            window.location.hash = "schemas";
        }
    }
    
    function refreshInitData() {
        $.getJSON("initData").done(onRefreshInitDataSuccess);
    }
    
    function onRefreshInitDataSuccess(response) {
        //brutally rebuild the UI
        schemasToolBarDiv.empty();
        schemaEditorDiv.empty();
        uiConfigDiv.empty();
        replicationDiv.empty();
//        remoteRepositoriesDiv.empty();
//        replicationCredentialsDiv.empty();
        authConfigDiv.empty();
        networkAndSecurityDiv.empty();
        
        design = response.design;
        schemasToolBar = new SchemasToolBar(schemasToolBarDiv, design.schemas);
        var firstSchema = getFirstSchemaAndType(design.schemas);
        if (firstSchema) {
            schemaEditor = new SchemaEditor(schemaEditorDiv, firstSchema.schema, firstSchema.type);
            schemaEditorDiv.show();
        }
        uiConfigEditor = new UiConfigEditor(uiConfigDiv, response.design.uiConfig);

        replicationEditor = new ReplicationEditor(replicationDiv, response.design.remoteRepositories, response.design.replicationCredentials);
//        remoteRepositoriesEditor = new RemoteRepositoriesEditor(remoteRepositoriesDiv, response.design.remoteRepositories);
//        replicationCredentialsEditor = new ReplicationCredentialsEditor(replicationCredentialsDiv, response.design.replicationCredentials);
        authConfigEditor = new AuthConfigEditor(authConfigDiv, response.design.authConfig);
        networkAndSecurity = new NetworkAndSecurity(networkAndSecurityDiv, response.isActiveSession, response.userId);
    }
    
    
    function handleNewWindowLocation() {
        var fragment = window.location.hash.substr(1);
        if (fragment != null && fragment != "") {
            hideAllSectionsExcept(fragment);
        }
    }
    
    function hideAllSectionsExcept(sectionId) {
        for (var id in sections) {
            if (id === sectionId) {
                sections[id].show();
                if (sectionId === "dashboard") {
                    //Need to call show on the dashboard once it is visible.
                    dashboard.show();
                }
            } else {
                sections[id].hide();
            }
        }
        window.scrollTo(0, 0);
    }
    
    function getFirstSchemaAndType(schemas) {
        var result = null;
        for (var type in schemas) {
            var schema = schemas[type];
            result = {
                    schema : schema,
                    type : type
            };
            break;
        }
        return result;
    } 
    
    function showSchemaEditorFor(objectType) {
        schemaEditorDiv.empty();
        var schema = design.schemas[objectType];
        schemaEditor = new SchemaEditor(schemaEditorDiv, schema, objectType);
        schemaEditorDiv.show();
    }
    self.showSchemaEditorFor = showSchemaEditorFor;
    
    function getSchema(type, remoteRepository) {
        if (remoteRepository) {
            return undefined;
        } else {
            return design.schemas[type];
        }
    }
    self.getSchema = getSchema;
    
    function getSchemaCount() {
        return Object.keys(design.schemas).length;
    }
    self.getSchemaCount = getSchemaCount;
    
    function onAuthenticationStateChange(isActiveSession) {
        if (!isActiveSession) {
            networkAndSecurity.setToUnauthenticated();
        }
        refreshInitData();
    }
    
    function createNewSchema(objectType, template) {
        if (design.schemas[objectType]) {
            notifications.alertError("A schema for type " + objectType+ " already exists.");
        } else {
            var newSchema = {};
            if (template) {
                newSchema = template;
            } else {
                newSchema = getDefaultSchema();
            }
            schemaEditorDiv.empty();
            schemaEditor = new SchemaEditor(schemaEditorDiv, newSchema, objectType);
            schemaEditorDiv.show();
        }
    }
    self.createNewSchema = createNewSchema;
    
    function getDefaultSchema() {
        return {
            "type": "object",
            "required": [
                         "name",
                         "description"
                         ],
                         "properties": {
                             "identifier": {
                                 "type": "string",
                                 "net.cnri.repository": {
                                     "type": {
                                         "autoGeneratedField": "handle"
                                     }
                                 }
                             },
                             "name": {
                                 "type": "string",
                                 "maxLength": 128,
                                 "title": "Name",
                                 "net.cnri.repository": {
                                     "preview": {
                                         "showInPreview": true,
                                         "isPrimary": true
                                     }
                                 }
                             },
                             "description": {
                                 "type": "string",
                                 "format": "textarea",
                                 "maxLength": 2048,
                                 "title": "Description",
                                 "net.cnri.repository": {
                                     "preview": {
                                         "showInPreview": true,
                                         "excludeTitle": true
                                     }
                                 }
                             }
                         }
        };
    }
    
    function saveUiConfig(uiConfig) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'uiConfig',
            type: 'PUT',
            data : JSON.stringify(uiConfig, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("UiConfig saved.");
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving this uiConfig. " + errorText);
            }
        });
    }
    self.saveUiConfig = saveUiConfig;
    
    function restartServer(doneCallback, errorCallback) {
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'restart',
            type: 'POST',
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            if (doneCallback) doneCallback(res); 
            //notifications.alertSuccess("...");
        }).fail(function (res, status, errorText) {
            if (errorCallback) errorCallback();
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error attempting to restart the server. " + errorText);
            }
        });
    }
    self.restartServer = restartServer;
    
    function performRestartServerWithDialog(promptMessageText) {
        networkAndSecurity.restartServer(promptMessageText);
    }
    self.performRestartServerWithDialog = performRestartServerWithDialog;
    
    function setToUnauthenticated() {
        authWidget.setUiToStateUnauthenticated();
    }
    self.setToUnauthenticated = setToUnauthenticated;
    
    function onServerRestarted() {
        authWidget.setUiToStateUnauthenticated();
        notifications.alertSuccess("Server has restarted.");
    }
    self.onServerRestarted = onServerRestarted;
    
    function saveAdminPassword(password) {
        notifications.clear();
        var data = {
                password : password
        };
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'adminPassword',
            type: 'PUT',
            data : JSON.stringify(data, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("Admin password saved.");
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving the admin password. " + errorText);
            }
        });
    }
    self.saveAdminPassword = saveAdminPassword;  
    
    function generateNewServerKeys(successCallback, errorCallback) {
        notifications.clear();
        var data = {};
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'generateKeys',
            type: 'POST',
            data : JSON.stringify(data, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("New server keys generated.");
            successCallback(res);
        }).fail(function (res, status, errorText) {
            if (errorCallback) errorCallback();
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error generating new server keys. " + errorText);
            }
        });
    }
    self.generateNewServerKeys = generateNewServerKeys;      
    
    function saveConfig(config, successCallback) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'config',
            type: 'PUT',
            data : JSON.stringify(config, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("Server configuration saved.");
            successCallback(res);
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving the server configuration. " + errorText);
            }
        });
    }
    self.saveConfig = saveConfig;
    
    function saveHandleMintingConfig(handleMintingConfig) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'handleMintingConfig',
            type: 'PUT',
            data : JSON.stringify(handleMintingConfig, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("Handle minting config saved.");
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving this handle minting config. " + errorText);
            }
        });
    }
    self.saveHandleMintingConfig = saveHandleMintingConfig;    
    
    function updateAllHandles(successCallback) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'updateHandles',
            type: 'POST',
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("Update in progress");
            if (successCallback) successCallback(); 
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error updating handles. " + errorText);
            }
        });
    }
    self.updateAllHandles = updateAllHandles;        
    
    function getHandleUpdateStatus(successCallback) {
        notifications.clear();
        $.ajax({
            url: 'updateHandles',
            type: 'GET',
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            if (successCallback) successCallback(res); 
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error getting handle update status. " + errorText);
            }
        });
    }
    self.getHandleUpdateStatus = getHandleUpdateStatus;     
    
    
    function saveRemoteRepositories(remoteRepositoriesUpdate) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'remoteRepositories',
            type: 'PUT',
            data : JSON.stringify(remoteRepositoriesUpdate, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, errorText) {
            notifications.alertSuccess("Remote registrars saved.");
        }).fail(function (res, status, xhr) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving remote registrars. " + errorText);
            }
        });
    }
    self.saveRemoteRepositories = saveRemoteRepositories;
    
    function saveReplicationCredentials(credentialsUpdate) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'replicationCredentials',
            type: 'PUT',
            data : JSON.stringify(credentialsUpdate, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("Replication credentials saved.");
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving replication credentials. " + errorText);
            }
        });
    }
    self.saveReplicationCredentials = saveReplicationCredentials;    
    
    function saveAuthConfig(authConfig) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'authConfig',
            type: 'PUT',
            data : JSON.stringify(authConfig, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            notifications.alertSuccess("Auth config saved.");
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving the auth config. " + errorText);
            }
        });
    }
    self.saveAuthConfig = saveAuthConfig;
    
    function saveSchema(schema, type) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'schemas/' + type,
            type: 'PUT',
            data : JSON.stringify(schema, null, " "),
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            design.schemas[type] = schema;
            schemasToolBar.refreshSelect();
            schemasToolBar.setSelected(type);
            notifications.alertSuccess("Schema " + type + " saved.");
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error saving this schema. " + errorText);
            }
        });
    }
    self.saveSchema = saveSchema;
    
    function deleteSchema(type) {
        notifications.clear();
        if (!authWidget.getIsActiveSession()) {
            notifications.alertError("Not authenticated.");
            return;
        }
        $.ajax({
            url: 'schemas/' + type,
            type: 'DELETE',
            contentType : "application/json; charset=utf-8",
            dataType : "json",
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            },
            statusCode: {
                401: function(xhr) {
                    authWidget.setUiToStateUnauthenticated();
                }
            }
        }).done(function (res, status, xhr) {
            delete design.schemas[type];
            schemasToolBar.refreshSelect();
            notifications.alertSuccess("Schema " + type + " deleted.");
        }).fail(function (res, status, errorText) {
            if (res.responseText != "")  {
                var message = JSON.parse(res.responseText);
                notifications.alertError(message.message);
            } else {
                notifications.alertError("There was an error deleting this schema. " + errorText);
            }
        });
    }
    self.deleteSchema = deleteSchema;
    
    
    function search(query, pageNum, pageSize, sortFields, onSuccess, onError) {
        var url = "objects/?query=" + encodeURIComponent(query) + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        if (sortFields) {
            url = url + "&sortFields=" + sortFields;
        }
        
        $.getJSON(url)
        .done(function (res, status, xhr) {
            notifications.clear();
            onSuccess(res, status, xhr);
        }).fail(function (res, status, xhr) {
            var message = JSON.parse(res.responseText);
            notifications.alertError(message.message);
            onError(res, status, xhr);
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
    
    function getObjectLocally(objectId, successCallback, errorCallback) {
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
            successCallback(res, status, xhr);
        }).fail(function (res, status, xhr) {
            errorCallback(res, status, xhr);
        });
    } 
    self.getObjectLocally = getObjectLocally;
    
    function getStatus(successCallback, errorCallback) {
        $.ajax({
            url: 'status/',
            type: 'GET',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            successCallback(res, status, xhr);
        }).fail(function (res, status, xhr) {
            errorCallback(res, status, xhr);
        });
    }
    self.getStatus = getStatus;
    
    function getCountByUser(successCallback, errorCallback) {
        $.ajax({
            url: 'countbyuser/',
            type: 'GET',
            beforeSend: function (xhr) {
                var csrfToken = getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(function (res, status, xhr) {
            successCallback(res, status, xhr);
        }).fail(function (res, status, xhr) {
            errorCallback(res, status, xhr);
        });
    }
    self.getCountByUser = getCountByUser;    
    
    function search(query, pageNum, pageSize, sortFields, onSuccess, onError) {
        var url = "objects/?query=" + encodeURIComponent(query) + "&pageNum=" + pageNum + "&pageSize=" + pageSize;
        if (sortFields) {
            url = url + "&sortFields=" + sortFields;
        }
        
        $.getJSON(url)
        .done(function (res, status, xhr) {
            notifications.clear();
            onSuccess(res, status, xhr);
        }).fail(function (res, status, xhr) {
            var message = JSON.parse(res.responseText);
            notifications.alertError(message.message);
            onError(res, status, xhr);
        });
    }
    self.search = search;
    
    function getObjectRemotely(remoteRepository, objectId, successCallBack, errorCallback) {
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
    self.getObjectRemotely = getObjectRemotely;
    
    constructor();
}

window.RegistrarAdminApp = RegistrarAdminApp;

})();