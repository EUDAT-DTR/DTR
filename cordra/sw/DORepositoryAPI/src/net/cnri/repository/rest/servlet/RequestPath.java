/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest.servlet;

import net.cnri.util.StringUtils;

public class RequestPath {
    
    public enum Type {
        QUERY,
        OBJECT,
        OBJECT_ATTRIBUTE,
        OBJECT_ELEMENT,
        OBJECT_ELEMENT_ATTRIBUTE,
        INVALID
    }
    
    private String handle;
    private String elementName;
    private String attributeName;
    private final RequestPath.Type type;
    
    static String urlDecode(String s) {
        return StringUtils.decodeURLIgnorePlus(s);
    }
    
    public RequestPath(String path) {
        if(!path.endsWith("/")) path = path + "/";
        if(path.equals("/")) {
            handle = null;
            elementName = null;
            attributeName = null;
            type = RequestPath.Type.QUERY;
            return;
        }
        int elIndex = path.indexOf("/el/");
        int attIndex;
        if(elIndex >= 0) attIndex = path.indexOf("/att/", elIndex + 4);
        else attIndex = path.indexOf("/att/");
//        // avoid /.../att/.../el/...
//        if(attIndex >= 0 && elIndex > attIndex) elIndex = -1;
        if(elIndex >= 0 && elIndex + 4 == path.length()) {
            type = RequestPath.Type.INVALID;
            return;
        } else if(attIndex >= 0 && attIndex + 5 == path.length()) {
            type = RequestPath.Type.INVALID;
            return;
        } else if(elIndex < 0 && attIndex < 0) {
            handle = urlDecode(path.substring(1,path.length()-1));
            elementName = null;
            attributeName = null;
        } else if(attIndex < 0) {
            handle = urlDecode(path.substring(1,elIndex));
            elementName = urlDecode(path.substring(elIndex+4,path.length()-1));
            attributeName = null;
        } else if(elIndex < 0) {
            handle = urlDecode(path.substring(1,attIndex));
            attributeName = urlDecode(path.substring(attIndex+5,path.length()-1));
            elementName = null;
        } else {
            handle = urlDecode(path.substring(1,elIndex));
            elementName = urlDecode(path.substring(elIndex+4,attIndex));
            attributeName = urlDecode(path.substring(attIndex+5,path.length()-1));
        }
        type = determineRequestType();
    }
    
    private RequestPath.Type determineRequestType() {
        if ("".equals(attributeName) || "".equals(elementName)) {
            return RequestPath.Type.INVALID;            
        } else if (handle == null && attributeName == null && elementName == null) {
            return RequestPath.Type.QUERY;                             //  /
        } else if (handle != null && attributeName == null && elementName == null) {
            return RequestPath.Type.OBJECT;                         //  /{URLenc handle}
        } else if (handle != null && attributeName != null && elementName == null) {
            return RequestPath.Type.OBJECT_ATTRIBUTE;               //  /{URLenc handle}/att/{URLenc attribute name}
        } else if (handle != null && attributeName == null && elementName != null) {
            return RequestPath.Type.OBJECT_ELEMENT;                 //  /{URLenc handle}/el/{URLenc element name}
        } else if (handle != null && attributeName != null && elementName != null) {
            return RequestPath.Type.OBJECT_ELEMENT_ATTRIBUTE;       //  /{URLenc handle}/el/{URLenc element name}/att/{URLenc attribute name}
        } else {
            return RequestPath.Type.INVALID;
        }
    }
    
    public RequestPath.Type getType() {
        return type;
    }

    public String getHandle() {
        return handle;
    }

    public String getElementName() {
        return elementName;
    }

    public String getAttributeName() {
        return attributeName;
    }
}
