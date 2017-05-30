/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

public class ServletErrorUtil {
    private static Gson gson = new Gson();
    
    public static void internalServerError(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        RegistrarErrorResponse errorResponse = new RegistrarErrorResponse("Something went wrong. Contact your sysadmin.");
        //Gson gson = GsonUtility.getGson();
        PrintWriter writer = resp.getWriter();
        gson.toJson(errorResponse, writer);
    }

    public static  void badRequest(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        RegistrarErrorResponse errorResponse = new RegistrarErrorResponse(message);
        //Gson gson = GsonUtility.getGson();
        PrintWriter writer = resp.getWriter();
        gson.toJson(errorResponse, writer);
    }

    public static  void notFound(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        RegistrarErrorResponse errorResponse = new RegistrarErrorResponse(message);
        //Gson gson = GsonUtility.getGson();
        PrintWriter writer = resp.getWriter();
        gson.toJson(errorResponse, writer);
    }
}
