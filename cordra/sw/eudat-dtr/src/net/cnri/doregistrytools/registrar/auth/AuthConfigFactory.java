/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

public class AuthConfigFactory {

    public static AuthConfig getDefaultAuthConfig() {

        AuthConfig result = new AuthConfig();
        
        DefaultAcls userAcls = new DefaultAcls();
        
        userAcls.defaultAclRead.add("admin");
        userAcls.defaultAclWrite.add("self");
        
        result.schemaAcls.put("User", userAcls);

        DefaultAcls remoteUserAcls = new DefaultAcls();

        userAcls.defaultAclRead.add("admin");
        userAcls.defaultAclWrite.add("self");
        
        result.schemaAcls.put("RemoteUser", userAcls);

        DefaultAcls groupAcls = new DefaultAcls();
        
        groupAcls.defaultAclRead.add("creator");
        groupAcls.defaultAclWrite.add("self");

        result.schemaAcls.put("Group", groupAcls);
        
        DefaultAcls schemaAcls = new DefaultAcls();
        schemaAcls.defaultAclRead.add("public");
        result.schemaAcls.put("Schema", schemaAcls);
        
        result.defaultAcls.defaultAclRead.add("public");
        result.defaultAcls.defaultAclWrite.add("creator");
        result.defaultAcls.aclCreate.add("authenticated");

        return result;
    }
}
