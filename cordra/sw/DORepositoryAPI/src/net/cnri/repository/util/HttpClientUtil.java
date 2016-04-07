/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.io.IOException;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class HttpClientUtil {
    public static String getJSONFromURL(String url) throws IOException {
    	// TODO as a future improvement avoid cost of constructing and shutting down client with each call
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet request = new HttpGet(url);  
            ResponseHandler<String> handler = new BasicResponseHandler();  
            String result = httpclient.execute(request, handler);   
            return result;
        } finally {
            httpclient.close();
        }
    }
}
