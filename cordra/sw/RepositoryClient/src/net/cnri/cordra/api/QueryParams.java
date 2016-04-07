package net.cnri.cordra.api;

/**
 * Parameters to a repository search, such as pagination and sorting.
 */
public class QueryParams {
    /**
     * Default query parameters.  Passing {@code null} to repository search methods amounts to using this.  No pagination and no sorting.
     */
    public static final QueryParams DEFAULT = new QueryParams(0,0);
    
    private final int pageNumber;
	private final int pageSize;
	/**
	 * Construct a QueryParams.
	 * @param pageNumber the page number to return.  Starts at 0.  Ignored if pageSize==0.
	 * @param pageSize the number of objects to return.  PageSize of 0 means return all.  
	 */
	public QueryParams(int pageNumber, int pageSize) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }
	
	public int getPageNumber() {
        return pageNumber;
    }
	
	public int getPageSize() {
        return pageSize;
    }
}
