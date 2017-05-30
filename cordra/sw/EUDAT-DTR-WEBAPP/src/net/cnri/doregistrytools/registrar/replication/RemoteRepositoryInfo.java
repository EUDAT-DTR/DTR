/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.replication;

import java.util.List;

import net.cnri.doregistrytools.registrar.jsonschema.Views;

import com.fasterxml.jackson.annotation.JsonView;

public class RemoteRepositoryInfo {
    public String baseUri;
    public long lastTimestamp = 0;
    public List<String> includeTypes;
    public List<String> excludeTypes;
    @JsonView(Views.Internal.class) public String username;
    @JsonView(Views.Internal.class) public String password;
}
