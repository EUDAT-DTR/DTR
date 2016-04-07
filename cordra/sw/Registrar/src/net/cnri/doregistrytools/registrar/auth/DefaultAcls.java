/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.util.ArrayList;
import java.util.List;

public class DefaultAcls {
    public List<String> defaultAclRead;
    public List<String> defaultAclWrite;
    public List<String> aclCreate;
    
    public DefaultAcls() {
        defaultAclRead = new ArrayList<String>();
        defaultAclWrite = new ArrayList<String>();
        aclCreate = new ArrayList<String>();
    }
}
