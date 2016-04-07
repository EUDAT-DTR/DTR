/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.cordra.api;

import java.io.InputStream;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

public interface CordraClient {

    CordraObject get(String id) throws CordraException;
    CordraObject get(String id, String username, String password) throws CordraException;
    
    InputStream getPayload(String id, String payloadName) throws CordraException;
    InputStream getPayload(String id, String payloadName, String username, String password) throws CordraException;
    
    CordraObject create(CordraObject d) throws CordraException;
    CordraObject create(CordraObject d, String username, String password) throws CordraException;

    CordraObject update(CordraObject d) throws CordraException;
    CordraObject update(CordraObject d, String username, String password) throws CordraException;

    void delete(String id) throws CordraException;
    void delete(String id, String username, String password) throws CordraException;

    SearchResults<CordraObject> search(String query) throws CordraException;
    SearchResults<CordraObject> search(String query, String username, String password) throws CordraException;
    
    SearchResults<String> searchHandles(String query) throws CordraException;
    SearchResults<String> searchHandles(String query, String username, String password) throws CordraException;
    
    SearchResults<CordraObject> list() throws CordraException;

    SearchResults<CordraObject> search(String query, QueryParams params) throws CordraException;
    SearchResults<CordraObject> search(String query, QueryParams params, String username, String password) throws CordraException;

    SearchResults<String> searchHandles(String query, QueryParams params) throws CordraException;
    SearchResults<String> searchHandles(String query, QueryParams params, String username, String password) throws CordraException;

    boolean authenticate() throws CordraException;
    boolean authenticate(String username, String password) throws CordraException;
    AuthResponse authenticateAndGetResponse(String username, String password) throws CordraException;
    
    default String getContentAsJson(String id) throws CordraException {
        CordraObject d = get(id);
        return new Gson().toJson(d.content);
    }

    default <T> T getContent(String id, Class<T> klass) throws CordraException {
        CordraObject d = get(id);
        return new Gson().fromJson(d.content, klass);
    }

    default CordraObject create(String type, String contentJson) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new JsonParser().parse(contentJson);
        return create(d);
    }
    
    default CordraObject create(String type, String contentJson, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new JsonParser().parse(contentJson);
        return create(d, username, password);
    }

    default CordraObject update(String id, String contentJson) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = new JsonParser().parse(contentJson);
        return update(d);
    }
    
    default CordraObject update(String id, String contentJson, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = new JsonParser().parse(contentJson);
        return update(d, username, password);
    }

    default CordraObject create(String type, Object content) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new Gson().toJsonTree(content);
        return create(d);
    }
    
    default CordraObject create(String type, Object content, String username, String password) throws CordraException {
        CordraObject d = new CordraObject();
        d.type = type;
        d.content = new Gson().toJsonTree(content);
        return create(d, username, password);
    }    

    default CordraObject update(String id, Object content) throws CordraException {
        CordraObject d = new CordraObject();
        d.id = id;
        d.content = new Gson().toJsonTree(content);
        return update(d);
    }
    
//    default <T> SearchResults<T> search(String query, Class<T> klass) throws RepositoryException {
//        
//    }

}
