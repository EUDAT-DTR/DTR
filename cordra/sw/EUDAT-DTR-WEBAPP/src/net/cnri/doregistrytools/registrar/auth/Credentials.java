/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import net.handle.hdllib.Util;

import org.apache.commons.codec.binary.Base64;

public class Credentials {
    private String username;
    private String password;

    public Credentials(String authHeader) {
        String encodedUsernameAndPassWord = getEncodedUserNameAndPassword(authHeader);
        String decodedAuthHeader = new String(Base64.decodeBase64(encodedUsernameAndPassWord.getBytes()));
        username = decodedAuthHeader.substring(0, decodedAuthHeader.indexOf(":"));
        password = decodedAuthHeader.substring(decodedAuthHeader.indexOf(":") + 1);
    }

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    public String getAuthHeader() {
        byte[] usernamePasswordBytes = Util.encodeString(username + ":" + password);
        String usernamePasswordBase64 = Base64.encodeBase64String(usernamePasswordBytes);
        return "Basic " + usernamePasswordBase64;
    }
    
    private String getEncodedUserNameAndPassword(String authHeader) {
        return authHeader.substring(authHeader.indexOf(" ") + 1);
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }
}
