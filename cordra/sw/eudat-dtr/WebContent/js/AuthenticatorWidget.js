/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function AuthenticatorWidget(containerDiv, onAuthenticationStateChangeCallback, isActiveSession, usernameParam, userIdParam) {
    var self = this;

    var signInButton = null;
    var signOutButton = null;
    var authenticatedLabel = null;

    var authenticatedDiv = null;
    var authenticateDiv = null;
    
    var usernameInput = null;
    var passwordInput = null;
    var authenticateButton = null;
    
    var dialogNotifications = null;
    var credentials = null;
    var userId = null;
    var username = null;
    
    var HEARTBEAT_TIMEOUT = 1000 * 30;
    
    var isHeartbeatInFlight = false;
    var ignoreNextHearbeatResponse = false;
    
    function constructor() {
        signInButton = $('<button type="button" class="btn btn-primary btn-sm">Sign In</button>');
        signInButton.click(onSignInClick);
        containerDiv.append(signInButton);
        
        authenticatedDiv = $('<div class="authenticatedDiv" style="display:none;"></div>');
        containerDiv.append(authenticatedDiv);
        var signOutForm = $('<form class="form-inline"></form>');
        authenticatedDiv.append(signOutForm);
        var signOutGroup = $('<div class="control-group"></div>');
        signOutForm.append(signOutGroup);
        

        authenticatedLabel = $('<span class="help-inline" style="margin-top: 5px;color:#777"></span>');
        signOutGroup.append(authenticatedLabel);
        signOutGroup.append(" ");
        signOutButton = $('<button type="button" class="btn btn-default btn-sm">Sign out</button>');
        signOutGroup.append(signOutButton);
        signOutButton.click(onSignOutButtonClick);
        
        buildAuthenticateDialog();
        if (isActiveSession) {
            credentials = new Credentials(usernameParam, ""); 
            userId = userIdParam;
            username = usernameParam;
            setAuthenticated();
            //onAuthenticateSuccess(null);
        }
        
        setTimeout(heartbeat, HEARTBEAT_TIMEOUT);
    }
    
    function buildAuthenticateDialog() {
        authenticateDiv = $('<div class="modal fade" tabindex="-1"></div>');
        
        var modalDialog = $('<div class="modal-dialog"></div>');
        authenticateDiv.append(modalDialog);
        
        var modalContent = $('<div class="modal-content"></div>');
        modalDialog.append(modalContent);
        
        var modalHeader = $('<div class="modal-header"></div>');
        modalContent.append(modalHeader);
        var closeButton = $('<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>');
        modalHeader.append(closeButton);

        var title = $('<h4 class="modal-title">Authenticate</h4>');
        modalHeader.append(title);
        
        var dialogNotificationsDiv = $('<div></div>');
        modalContent.append(dialogNotificationsDiv);
        dialogNotifications = new Notifications(dialogNotificationsDiv); 
        
        var modalBody = $('<div class="modal-body"></div>');
        modalContent.append(modalBody);
        
        var dialogNotificationsDiv = $('<div></div>');
        modalContent.append(dialogNotificationsDiv);
        dialogNotifications = new Notifications(dialogNotificationsDiv); 
        
        
        var secretForm = $('<form class="form-inline" role="form"></form>');
        modalBody.append(secretForm);
        usernameInput = $('<input type="text" class="form-control input-sm username-input" placeholder="Username">');
        secretForm.append(usernameInput);
        
        authenticateDiv.on('shown.bs.modal', function () {
            usernameInput.focus();
        });
        
        passwordInput = $('<input type="password" class="form-control input-sm" placeholder="Password">');
        passwordInput.keypress(function(event){
            if(event.keyCode == 13){ 
                event.preventDefault();
                onAuthenticateButtonClick(event);
            }
        });
        secretForm.append(passwordInput);
        secretForm.append(" ");
        authenticateButton = $('<button type="button" class="btn btn-sm btn-primary" style="min-width: 130px;" data-loading-text="Authenticating...">Authenticate</button>');
        secretForm.append(authenticateButton);
        authenticateButton.click(onAuthenticateButtonClick);
    }
    
    function onSignInClick() {
        usernameInput.focus();
        authenticateDiv.modal({ keyboard: true});
    }
    
    function onAuthenticateButtonClick(e) {
        e.preventDefault();
        var password = passwordInput.val();

        var username = usernameInput.val();
        if (username === "") {
            dialogNotifications.alertError("Missing username.");
            return;
        }
        credentials = new Credentials(username, password);
        var authHeader = credentials.getAuthorizationHeader();

        if (isHeartbeatInFlight) {
            ignoreNextHearbeatResponse = true;
        }
        
        $.ajax({
            url: 'sessions',
            type: 'POST',
            beforeSend: function (xhr){ 
                xhr.setRequestHeader('Authorization', authHeader); 
            }
        }).done(onAuthenticateSuccess).fail(onAuthenticateError);
        
    }
    
    function getIsActiveSession() {
        return isActiveSession;
    }
    self.getIsActiveSession = getIsActiveSession;
    
    function onSignOutButtonClick(e) {
        e.preventDefault();
        
        if (isHeartbeatInFlight) {
            ignoreNextHearbeatResponse = true;
        }
        $.ajax({
            url: 'sessions/this',
            type: 'DELETE',
            beforeSend: function (xhr) {
                var csrfToken = APP.getCsrfCookieToken();
                if (csrfToken) {
                    xhr.setRequestHeader('X-Csrf-Token', csrfToken); 
                }
            }
        }).done(onSignOutSuccess).fail(onSignOutError);
    }
    
    function onSignOutSuccess() {
        APP.notifications.clear();
        setUiToStateUnauthenticated();
    }
    
    function setUiToStateUnauthenticated() {
        if (!isActiveSession) return;
        isActiveSession = false;
        authenticatedLabel.text("");
        signInButton.show();
        authenticatedDiv.hide();
        userId = null;
        credentials = null;
        onAuthenticationStateChangeCallback(isActiveSession);
    }
    self.setUiToStateUnauthenticated = setUiToStateUnauthenticated;
    
    function onSignOutError() {
        setUiToStateUnauthenticated();
    }    
    
    function setAuthenticated() {
        isActiveSession = true;
        APP.notifications.clear();
        dialogNotifications.clear();
        usernameInput.val("");
        passwordInput.val("");
        signInButton.hide();
        authenticateButton.button('reset');
        //authenticatedLabel.text(credentials.getUsername());
        authenticatedLabel.text(username);
        
        authenticateDiv.modal('hide');
        authenticateDiv.hide();
        authenticatedDiv.show();
        //credentials.setIsAuthenticated(true);
    }
    
    function onAuthenticateSuccess(response) {
        userId = response.userId;
        username = response.username;
        setAuthenticated();
        onAuthenticationStateChangeCallback(isActiveSession);
    }
    
    function onAuthenticateError(response) {
        var msg = "The username or password you entered is incorrect";
        if (response.message != undefined) {
            msg = msg + " " + response.message;
        }
        dialogNotifications.alertError(msg);
        authenticateButton.button('reset');
    }
    
    function getCurrentUserId() {
        return userId;
    }
    self.getCurrentUserId = getCurrentUserId;
    
    function heartbeat() {
        isHeartbeatInFlight = true;
        $.getJSON("sessions").done(function(resp) {
            isHeartbeatInFlight = false;
            if (ignoreNextHearbeatResponse) {
                ignoreNextHearbeatResponse = false;
                return;
            }
            if (resp.isActiveSession) {
                if (!isActiveSession) {
                    onAuthenticateSuccess(resp);
                }
            } else {
                if (isActiveSession) {
                    onSignOutSuccess();
                }
            }
        }).fail(function() {
            isHeartbeatInFlight = false;
            if (ignoreNextHearbeatResponse) {
                ignoreNextHearbeatResponse = false;
                return;
            }
            if (isActiveSession) {
                onSignOutSuccess();
            }
            APP.notifications.alertError("The repository could not be reached.");
        });
        setTimeout(heartbeat, HEARTBEAT_TIMEOUT);
    }
    
    constructor();
}