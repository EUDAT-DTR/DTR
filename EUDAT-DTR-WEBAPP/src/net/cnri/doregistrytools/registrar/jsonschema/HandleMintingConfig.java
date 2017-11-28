/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)

public class HandleMintingConfig {
    
    public String baseUri;
    public Map<String, List<LinkConfig>> schemaSpecificLinks;
    public List<LinkConfig> defaultLinks;
    
    public static HandleMintingConfig getDefaultConfig() {
        HandleMintingConfig result = new HandleMintingConfig();
        result.defaultLinks = new ArrayList<LinkConfig>();
        LinkConfig jsonLink = new LinkConfig();
        jsonLink.type = "json";
        result.defaultLinks.add(jsonLink);
        
        LinkConfig uiLink = new LinkConfig();
        uiLink.type = "ui";
        uiLink.primary = true;
        result.defaultLinks.add(uiLink);
        
        return result;
    }
}
