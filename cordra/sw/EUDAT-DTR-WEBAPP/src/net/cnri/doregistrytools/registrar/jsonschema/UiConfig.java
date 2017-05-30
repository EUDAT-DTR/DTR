/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.List;

public class UiConfig {
    public String title;
    public String relationshipsButtonText;
    public boolean allowUserToSpecifySuffixOnCreate = true;
    public String initialQuery;
    public String initialSortFields;
    List<NavBarLink> navBarLinks;
    
    public static class NavBarLink {
        public String type; //document or query
        public String title;
        
        //if query
        public String query;
        public String sortFields;
        
        //if document
        public String dataElementName;
        
    }
}
