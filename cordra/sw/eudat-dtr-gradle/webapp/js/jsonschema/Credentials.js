/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

function Credentials(username, password) {
    var self = this;
    var isAuthenticated = false;
    
    function constructor() {
        
    }
    
    function setIsAuthenticated(isAuthenticatedParam) {
        isAuthenticated = isAuthenticatedParam;
    }
    self.setIsAuthenticated = setIsAuthenticated;
    
    function getIsAuthenticated() {
        return isAuthenticated;
    }
    self.getIsAuthenticated = getIsAuthenticated;
    
    function getAuthorizationHeader() {
        var usernameColonPassword = username + ":" + password;
        var bytes = cnri.util.Encoder.Utf8.bytes(usernameColonPassword);
        var base64String = cnri.util.Encoder.Base64.string(bytes);
        var result = "Basic " + base64String;
        return result;
    }
    self.getAuthorizationHeader = getAuthorizationHeader;
    
    function getUsername() {
        return username;
    }
    self.getUsername = getUsername;
    
    constructor();
}