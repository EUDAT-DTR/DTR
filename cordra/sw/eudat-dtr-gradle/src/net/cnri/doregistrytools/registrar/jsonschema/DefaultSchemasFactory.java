/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.InputStream;
import java.io.InputStreamReader;

import net.cnri.util.StreamUtil;

public class DefaultSchemasFactory {

    public static String getSchemaSchema() {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream("schema.schema.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }
    
    public static String getDefaultUserSchema() {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream("schema.user.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }
    
    public static String getDefaultGroupSchema() {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream("schema.group.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }

    public static String getDefaultRemoteUserSchema() {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream("schema.ruser.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }
    
    public static String getDefaultDocumentSchema() {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream("schema.document.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }

    public static String getDefaultDataTypeSchema() {
        InputStream resource = DefaultSchemasFactory.class.getResourceAsStream("schema.datatype.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }

}
