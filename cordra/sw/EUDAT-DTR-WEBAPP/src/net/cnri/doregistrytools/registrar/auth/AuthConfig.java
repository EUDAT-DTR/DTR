/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.util.HashMap;
import java.util.Map;

public class AuthConfig {
    public Map<String, DefaultAcls> schemaAcls;
    public DefaultAcls defaultAcls;
    
    public AuthConfig() {
        schemaAcls = new HashMap<String, DefaultAcls>();
        defaultAcls = new DefaultAcls();
    }
    
    public DefaultAcls getAclForObjectType(String objectType) {
        DefaultAcls res = schemaAcls.get(objectType);
        if (res == null) {
            res = defaultAcls;
        }
        return res;
    }
}
