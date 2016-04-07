/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import java.util.List;
import java.util.Map;

/**
 * Parameters to a repository search, such as pagination and sorting.
 */
public class QueryParams {
    /**
     * Default query parameters.  Passing {@code null} to repository search methods amounts to using this.  No pagination and no sorting.
     */
    public static final QueryParams DEFAULT = new QueryParams(0,0,null);
    
    private final int pageOffset;
	private final int pageSize;
	private final List<SortField> sortFields;
	private final List<String> returnedFields;
	private final Map<String, String> extras;
	
//	public QueryParams(int pageNumber, int pageSize, List<String> sortFields) {
//		this.pageOffset = pageNumber;
//		this.pageSize = pageSize;
//		List<SortField> sortFieldsWithReverse = new ArrayList<SortField>(sortFields.size());
//		for(String sortField : sortFields) {
//		    sortFieldsWithReverse.add(new SortField(sortField,false));
//		}
//		this.sortFields = sortFieldsWithReverse;
//	}
//
	/**
	 * Construct a QueryParams.
	 * @param pageNumber the page number to return.  Starts at 0.  Ignored if pageSize==0.
	 * @param pageSize the number of objects to return.  PageSize of 0 means return all.  
	 * @param sortFields fields to sort over.  If null means no sort.
	 */
	public QueryParams(int pageNumber, int pageSize, List<SortField> sortFields) {
	    this(pageNumber,pageSize,sortFields,null,null);
	}

    /**
     * Construct a QueryParams.
     * @param pageNumber the page number to return.  Starts at 0.  Ignored if pageSize==0.
     * @param pageSize the number of objects to return.  PageSize of 0 means return all.  
     * @param sortFields fields to sort over.  If null means no sort.
     * @param returnedFields fields to include in the query result.  This means the DigitalObjects returned by a search will only partially represent the matched objects.
     */
    public QueryParams(int pageNumber, int pageSize, List<SortField> sortFields, List<String> returnedFields) {
        this(pageNumber,pageSize,sortFields,returnedFields,null);
    }

    /**
     * Construct a QueryParams.
     * @param pageNumber the page number to return.  Starts at 0.  Ignored if pageSize==0.
     * @param pageSize the number of objects to return.  PageSize of 0 means return all.  
     * @param sortFields fields to sort over.  If null means no sort.
     * @param returnedFields fields to include in the query result.  This means the DigitalObjects returned by a search will only partially represent the matched objects.
     * @param extras extra parameters to include in the query.  The repository implementation is responsible for interpreting the parameters.
     */
    public QueryParams(int pageNumber, int pageSize, List<SortField> sortFields, List<String> returnedFields, Map<String,String> extras) {
        this.pageOffset = pageNumber;
        this.pageSize = pageSize;
        this.sortFields = sortFields;
        this.returnedFields = returnedFields;
        this.extras = extras;
    }
    
	public int getPageOffset() {
		return pageOffset;
	}

	public int getPageSize() {
		return pageSize;
	}
	
	public List<SortField> getSortFields() {
		return sortFields;
	}

    public List<String> getReturnedFields() {
        return returnedFields;
    }
    
    public Map<String, String> getExtras() {
        return extras;
    }
}
