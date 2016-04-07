/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest;

import java.util.List;

import net.cnri.repository.util.RepositoryJsonSerializerV2.DOView;

public class QueryResultsForJson {
    private List<DOView> results;
    private int numResults;
    private int pageOffSet;
    private int pageSize;
    
    public QueryResultsForJson(List<DOView> results, int numResults, int pageOffSet, int pageSize) {
        this.results = results;
        this.numResults = numResults;
        this.pageOffSet = pageOffSet;
        this.pageSize = pageSize;
    }
    
    public List<DOView> getResults() {
        return results;
    }
    
    public int getNumResults() {
        return numResults;
    }

    public int getPageOffSet() {
        return pageOffSet;
    }

    public int getPageSize() {
        return pageSize;
    }
}
