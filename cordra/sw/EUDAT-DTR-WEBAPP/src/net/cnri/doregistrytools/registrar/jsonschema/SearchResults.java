/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.List;

public class SearchResults {
    int size;
    int pageNum;
    int pageSize;
    List<ContentPlusMeta> results;
    
    public SearchResults(int size, int pageNum, int pageSize, List<ContentPlusMeta> results) {
        this.size = size;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.results = results;
    }
}
