/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function NetworkAndSecurity(containerDiv, isActiveSession, userId) {
    var self = this;
    var adminPasswordInput = null;
    var serverRestartingBanner = null;
    var unauthenticatedDiv = null;
    var authenitcatedContainerDiv = null;
    var newKeysButton = null;
    
    function constructor() {
        unauthenticatedDiv = $('<div style="display:none"></div>');
        containerDiv.append(unauthenticatedDiv);
        
        var notAdminMessage = $('<p>You need to be authenticated as admin in order to modify these settings.</p>');
        unauthenticatedDiv.append(notAdminMessage);
        
        authenitcatedContainerDiv = $('<div style="display:none"></div>');
        containerDiv.append(authenitcatedContainerDiv);
        
        if (isActiveSession && userId === "admin") {
            buildRestartServerWidget();
            buildAdminPasswordEditor();
            buildServerConfigEditor();
            buildGenerateNewKeysEditor();
            authenitcatedContainerDiv.show();
        } else {
            unauthenticatedDiv.show();
        }
    }
    
    function buildRestartServerWidget() {
        var restartServerDiv = $('<div style="padding-bottom:10px"></div>');
        authenitcatedContainerDiv.append(restartServerDiv);
        
        var restartButton = $('<button class="btn btn-sm btn-danger">Restart Server</button>');
        restartServerDiv.append(restartButton);
        
        restartButton.click(onRestartButtonClick);
    }
    
    function onRestartButtonClick(e) {
        restartServer();
    }
    
    function buildAdminPasswordEditor() {
        var adminPasswordDiv = $('<div class="well"></div>');
        authenitcatedContainerDiv.append(adminPasswordDiv);
        
        var toolBarDiv = $('<div class="object-editor-toolbar"></div>');
        adminPasswordDiv.append(toolBarDiv);
        
        var saveButton = $('<button class="btn btn-sm btn-default">Save Admin Password</button>');
        toolBarDiv.append(saveButton);
        saveButton.click(saveAdminPassword);
        
        var form = $('<form class="form-horizontal"></form>');
        adminPasswordDiv.append(form);
        
        var group = $('<div class="form-group"></div>');
        form.append(group);
        
        var label = $('<label for="adminPasswordInput" class="col-sm-2 control-label">Admin Password</label>');
        group.append(label);

        var div = $('<div class="col-sm-10"></div>');
        group.append(div);
        
        adminPasswordInput = $('<input type="password" class="form-control" id="adminPasswordInput" ></input>');
        div.append(adminPasswordInput);
        adminPasswordInput.val();
    }
    
    function restartServer(promptMessageText) {
        var restartDialog = new ModalRestartDialog(promptMessageText);
        restartDialog.show();
    }
    self.restartServer = restartServer;
        
    function buildServerConfigEditor() {
        var serverConfigDiv = $('<div class="well"></div>');
        authenitcatedContainerDiv.append(serverConfigDiv);
        var serverConfigEditor = new ServerConfigEditor(serverConfigDiv);
    }
    
    function buildGenerateNewKeysEditor() {
        var generateKeysDiv = $('<div class="well"></div>');
        
        authenitcatedContainerDiv.append(generateKeysDiv);
        var newKeysMessage = $('<p>Clicking the button below will generate and store a new public/private key pair for your repository. The Handle System will be updated with your public key. You should restart your server after performing this operation.</p>');
        generateKeysDiv.append(newKeysMessage);
        
        newKeysButton = $('<button class="btn btn-sm btn-danger" data-loading-text="Please wait...">Generate new server key pair</button>');
        generateKeysDiv.append(newKeysButton);
        
        newKeysButton.click(onNewKeysButtonClick);
    }
    
    function onNewKeysButtonClick() {
        newKeysButton.button('loading');
        APP.generateNewServerKeys(onGeneratedNewServerKeysSuccess, onGeneratedNewServerKeysError);
    }
    
    function onGeneratedNewServerKeysSuccess(res) {
        newKeysButton.button('reset');
        restartServer("New server keys have been generated and stored. It is recommended that you restart the server now. Do you want to restart?");
    }
    
    function onGeneratedNewServerKeysError(res) {
        newKeysButton.button('reset');
    }
    
    function saveAdminPassword() {
        var password = adminPasswordInput.val();
        adminPasswordInput.val("");
        APP.saveAdminPassword(password);
    }
    
    function setToUnauthenticated() {
        authenitcatedContainerDiv.hide();
        unauthenticatedDiv.show();
    }
    self.setToUnauthenticated = setToUnauthenticated;
    
    constructor();
}