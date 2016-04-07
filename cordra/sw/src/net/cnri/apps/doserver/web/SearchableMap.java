/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.web;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.apps.doserver.web.MetadataUtil;
import net.cnri.apps.doserver.web.VelocityUtil;
import net.cnri.do_api.*;
import net.cnri.dobj.*;

public class SearchableMap extends HashMap {
  static final Logger logger = LoggerFactory.getLogger(SearchableMap.class);
  
  Repository repo;
  
  public SearchableMap(Repository repo) {
    this.repo = repo;
    put("todaysDate", MetadataUtil.formatDate(new Date()));
    put("Util", VelocityUtil.class);
    put("repo", repo);
    put("do", repo);
  }
  
  
  public SearchableMap getObject(String objectID) throws java.io.IOException  {
    DigitalObject obj = null;

    try {
      obj = repo.getDigitalObject(objectID);
    } catch (DOException e) {
      logger.error("Error getting object ("+objectID+")",e);
    }
    SearchableMap objMap = new SearchableMap(repo);
    objMap.put("id", objectID);
    if(obj==null) return objMap;
    
    objMap.put("do", obj);
    
    for(HeaderItem att : obj.getAttributes()) {
      objMap.put("att:"+att.getName(), att.getValue());
    }
    ArrayList elements = new ArrayList();
    for(String elementID : obj.listDataElements()) {
      SearchableMap elementMap = new SearchableMap(repo);
      elementMap.put("objectid", objectID);
      elementMap.put("elementid", elementID);
      for(HeaderItem att : obj.getDataElement(elementID).getAttributes()) {
        elementMap.put("att:"+att.getName(), att.getValue());
      }
      elements.add(elementMap);
      objMap.put("element:"+elementID, elementMap);
    }
    objMap.put("elements", elements);
    return objMap;
  }
  
  
  public List doSearch(String queryStr) {
    ArrayList results = new ArrayList<HashMap<String,String>>();
    try {
      HeaderSet queryAtts = new HeaderSet();
      queryAtts.addHeader("query", queryStr);
      StreamPair io = repo.performOperation("1037/search", queryAtts);
      InputStream in = io.getInputStream();
      HeaderSet result = new HeaderSet();
      int resultCount = 0;
      while(result.readHeaders(in)) {
        
        if(!result.getMessageType().equalsIgnoreCase("result")) continue; // ignore non-result lines (ie results metadata)
        
        SearchableMap map = new SearchableMap(repo);
        for(HeaderItem item : result) {
          map.put("att_"+item.getName(), item.getValue());
        }
        results.add(map);
        resultCount++;
      }
      System.out.println("found "+resultCount+" results for search "+queryStr);
      io.close();
    } catch (Exception e) {
      System.out.println("Error performing search: "+e+" search: "+queryStr);
    }
    return results;
  }

  public List doSearch(HeaderSet queryAtts) {
    ArrayList results = new ArrayList<HashMap<String,String>>();
    String queryStr = queryAtts.getStringHeader("query", "");
    try {
      StreamPair io = repo.performOperation("1037/search", queryAtts);
      InputStream in = io.getInputStream();
      HeaderSet result = new HeaderSet();
      int resultCount = 0;
      while(result.readHeaders(in)) {

        if(!result.getMessageType().equalsIgnoreCase("result")) continue; // ignore non-result lines (ie results metadata)

        SearchableMap map = new SearchableMap(repo);
        for(HeaderItem item : result) {
          map.put("att_"+item.getName(), item.getValue());
        }
        results.add(map);
        resultCount++;
      }
      System.out.println("found "+resultCount+" results for search "+queryStr);
      io.close();
    } catch (Exception e) {
      System.out.println("Error performing search: "+e+" search: "+queryStr);
    }
    return results;
  }
}

