/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.rest.QueryResultsForJson;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;
import net.cnri.repository.search.QueryResults;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.search.SortField;
import net.cnri.repository.util.CollectionUtil;
import net.cnri.repository.util.RepositoryJsonSerializerV2;
import net.cnri.repository.util.RepositoryJsonSerializerV2.DOView;
import net.cnri.util.StringUtils;

public class QueryRequestHandler extends RequestHandler {

    public QueryRequestHandler(RequestPath path, HttpServletRequest req, HttpServletResponse res, Repository repository) {
        super(path, req, res, repository);
    }

    //TODO add param to list handles only
    //TODO stream json  
    @Override
    protected void handleGet() throws RepositoryException, IOException{
        String queryString = req.getParameter("query");
        String json = null;
        if (queryString != null) {
            Query query = new RawQuery(queryString);
            QueryParams queryParams = getQueryParamsFromRequest(req);
            QueryResults<DigitalObject> resultsIter = (QueryResults<DigitalObject>) repository.search(query, queryParams);
//            List<DigitalObject> resultObjects = CollectionUtil.asList(resultsIter);
//            json = RepositoryJSONSerializer.toJSON(resultObjects, new ArrayList<String>());
            json = buildResultJson(resultsIter, queryParams);
        } else {
            json = RepositoryJsonSerializerV2.toJSON(repository);
        }
        res.setContentType(JSON);
        res.setCharacterEncoding("UTF-8");
        Writer w = res.getWriter();
        w.write(json);
    }
    
    private static String buildResultJson(QueryResults<DigitalObject> resultsIter, QueryParams queryParams) throws RepositoryException { 
//      String result = null;
//      result += "{ \"numResults\" : \""+ numResults +"\", \"pageOffSet\" : \""+ pageOffSet +"\", \"pageSize\" : \""+ pageSize +"\", \"results\" : "+ objectsAsJson +"}";
        
        int numResults = resultsIter.size();
        int pageOffSet = queryParams.getPageOffset();
        int pageSize = queryParams.getPageSize();
        List<DigitalObject> resultObjects = CollectionUtil.asList(resultsIter);
        List<DOView> resultViews = RepositoryJsonSerializerV2.toDOViews(resultObjects, new ArrayList<String>());
        QueryResultsForJson queryResultsForJson = new QueryResultsForJson(resultViews, numResults, pageOffSet, pageSize);
        Gson gsonInstance = new GsonBuilder().setPrettyPrinting().create();
        return gsonInstance.toJson(queryResultsForJson);
    }
    
//    public static class QueryResultsForJson {
//        private List<DOView> results;
//        private int numResults;
//        private int pageOffSet;
//        private int pageSize;
//        
//        public QueryResultsForJson(List<DOView> results, int numResults, int pageOffSet, int pageSize) {
//            this.results = results;
//            this.numResults = numResults;
//            this.pageOffSet = pageOffSet;
//            this.pageSize = pageSize;
//        }
//    }
    
    private static QueryParams getQueryParamsFromRequest(HttpServletRequest req) {
        String returnedFields = req.getParameter("returnedFields");
        String sortFields     = req.getParameter("sortFields");
        String pageSize       = req.getParameter("pageSize");
        String pageOffset     = req.getParameter("pageOffset");
        List<String> returnedFieldsList = getReturnedFieldsFromParam(returnedFields);
        List<SortField> sortFieldsList = getSortFieldsFromParam(sortFields);
        int pageSizeInt = 0;
        int pageOffsetInt = 0;
        if (pageSize != null) {
            pageSizeInt = Integer.parseInt(pageSize);
        }
        if (pageOffset != null) {
            pageOffsetInt = Integer.parseInt(pageOffset);
        }
        return new QueryParams(pageOffsetInt, pageSizeInt, sortFieldsList, returnedFieldsList);
    }
    
    private static List<String> getReturnedFieldsFromParam(String returnedFields) {
        if (returnedFields == null || "".equals(returnedFields)) {
            return null;
        } else {
            return getFieldsFromString(returnedFields);
        }
    }
    
    private static List<SortField> getSortFieldsFromParam(String sortFields) {
        if (sortFields == null || "".equals(sortFields)) {
            return null;
        } else {
            List<SortField> result = new ArrayList<SortField>();
            List<String> sortFieldStrings = getFieldsFromString(sortFields);
            for (String sortFieldString : sortFieldStrings) {
                result.add(getSortFieldFromString(sortFieldString));
            }
            return result;
        }
    }
    
    private static SortField getSortFieldFromString(String sortFieldString) {
        String[] terms = sortFieldString.split(" ");
        boolean reverse = false;
        if (terms.length > 1) {
            String direction = terms[1];
            if ("DESC".equals(direction)) reverse = true;
        }
        String fieldName = terms[0];
        return new SortField(fieldName, reverse);
    }
    
    private static List<String> getFieldsFromString(String s) {
        return Arrays.asList(s.split(","));
    }

    @Override
    protected void handlePut() throws RepositoryException, IOException{
        res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        res.setHeader("Allow","GET, HEAD, POST");
    }

    @Override
    protected void handlePost() throws RepositoryException, IOException{
        DigitalObject dobj = repository.createDigitalObject(null);
        List<String> elementsToInclude = Collections.emptyList();
        String json = RepositoryJsonSerializerV2.toJSON(dobj, elementsToInclude);
        res.setStatus(HttpServletResponse.SC_CREATED);
        String uri = req.getContextPath() + "/" + StringUtils.encodeURLComponent(dobj.getHandle());
        res.setHeader("Location",uri);
        Writer w = res.getWriter();
        w.write(json);
    }

    @Override
    protected void handleDelete() throws RepositoryException, IOException{
        res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        res.setHeader("Allow","GET, HEAD, POST");
    }
}
