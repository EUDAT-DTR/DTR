package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.cnri.doregistrytools.registrar.auth.QueryRestrictor;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.RepositoryException;
import net.cnri.util.StreamUtil;
import net.cnri.util.StringUtils;

@WebServlet({"/objects/*"})
@MultipartConfig
@SuppressWarnings("static-method")
public class ObjectServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(ObjectServlet.class);
    private static final String JSON = "json";
 
    private RegistrarService registrar;
    private Gson gson;
    
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = new Gson();
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String objectId = req.getPathInfo();
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        if (objectId == null || objectId.isEmpty()) {
            String query = req.getParameter("query");
            if (query == null) { 
                ServletErrorUtil.badRequest(resp, "Missing object id in GET");
                return;
            } else {
                doSearch(req, resp, query);
                return;
            }
        }
        String jsonPointer = req.getParameter("jsonPointer");
        String payload = req.getParameter("payload");
        boolean isWantText = ServletUtil.getBooleanParameter(req, "text");
        boolean isFull = ServletUtil.getBooleanParameter(req, "full");
        boolean isWantXml = ServletUtil.getBooleanParameter(req, "xml");
        
        if (payload != null) {
            doGetPayload(req, resp, objectId, payload);
        } else if (jsonPointer == null) {
            if (isFull) {
                getObjectPlusMetadata(resp, objectId, isWantXml);
            } else {
                goGetWholeObject(resp, objectId, isWantText, isWantXml);
            }
        } else {
            if (JsonUtil.isValidJsonPointer(jsonPointer)) {
                doGetJsonPointer(req, resp, objectId, jsonPointer, isWantText, isWantXml);
            } else {
                ServletErrorUtil.badRequest(resp, "Invalid JSON Pointer " + jsonPointer);
            }
        }
    }
    
    private static String jsonToXml(String json) {
        return "<unimplemented/>";
    }

    private void getObjectPlusMetadata(HttpServletResponse resp, String objectId, boolean isWantXml) throws IOException {
        try {
            DigitalObject dobj = registrar.getDigitalObject(objectId);
            String jsonData = dobj.getAttribute("json");
            String type = dobj.getAttribute("type");
            if (jsonData == null || type == null) {
                ServletErrorUtil.notFound(resp, "Not a valid registry object");
                return;
            }
            String remoteRepository = dobj.getAttribute("remoteRepository");
            resp.setHeader("Location", StringUtils.encodeURLPath("/objects/" + dobj.getHandle()));
            resp.setHeader("X-Schema", type);
            resp.setHeader("Origin", remoteRepository);
            
            ContentPlusMeta contentPlusMeta = RegistrarService.createContentPlusMeta(dobj);
            String json = gson.toJson(contentPlusMeta);
            PrintWriter w = resp.getWriter();
            if (isWantXml) {
                String xml = jsonToXml(json);
                w.write(xml);  
            } else {
                w.write(json);                
            }
        } catch (NoSuchDigitalObjectException e) { 
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (RepositoryException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        } 
    }
    
    private void goGetWholeObject(HttpServletResponse resp, String objectId, boolean isWantText, boolean isWantXml) throws IOException {
        try {
            DigitalObject dobj = registrar.getDigitalObject(objectId);
            String jsonData = dobj.getAttribute("json");
            String type = dobj.getAttribute("type");
            if (jsonData == null || type == null) {
                ServletErrorUtil.notFound(resp, "Not a valid registry object");
                return;
            }
            String remoteRepository = dobj.getAttribute("remoteRepository");
            resp.setHeader("Location", StringUtils.encodeURLPath("/objects/" + dobj.getHandle()));
            resp.setHeader("X-Schema", type);
            resp.setHeader("Origin", remoteRepository);
            if (isWantText) {
                JsonNode jsonNode = JsonUtil.parseJson(jsonData);
                String mediaType = registrar.getMediaType(type, remoteRepository, jsonNode, "");
                writeText(resp, jsonNode, mediaType);
            } else {
                PrintWriter w = resp.getWriter();
                if (isWantXml) {
                    String xml = jsonToXml(jsonData);
                    w.write(xml);  
                } else {
                    w.write(jsonData);                
                }
            }
        } catch (InvalidException e) { 
            ServletErrorUtil.internalServerError(resp); 
            logger.error("InvalidException in doGet", e);
        } catch (NoSuchDigitalObjectException e) { 
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (RepositoryException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        }
    }

    private void doGetPayload(HttpServletRequest req, HttpServletResponse resp, String objectId, String payload) throws IOException {
        ObjectComponent objectComponent = null;
        boolean metadata = ServletUtil.getBooleanParameter(req, "metadata");
        try {
            Range range = getRangeFromRequest(req);
            objectComponent = registrar.getObjectComponent(objectId, payload, metadata, range.getStart(), range.getEnd());
            if (!(objectComponent instanceof ObjectComponentStream)) {
                ServletErrorUtil.notFound(resp, "No payload " + payload + " in object " + objectId);
                return;
            }
            ObjectComponentStream objectComponentStream = (ObjectComponentStream) objectComponent;
            sendPayloadResponse(req, resp, metadata, objectComponentStream);
        } catch (RepositoryException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        } catch (InvalidException e) {
            ServletErrorUtil.internalServerError(resp); 
            logger.error("InvalidException in doGet", e);
        } finally {
            if (objectComponent instanceof ObjectComponentStream && ((ObjectComponentStream) objectComponent).in != null) {
                try { ((ObjectComponentStream) objectComponent).in.close(); } catch (Exception e) { }
            }
        }
    }
    
    private void doGetJsonPointer(HttpServletRequest req, HttpServletResponse resp, String objectId, String jsonPointer, boolean isWantText, boolean isWantXml) throws IOException {
        ObjectComponent objectComponent = null;
        try {
            boolean metadata = ServletUtil.getBooleanParameter(req, "metadata");
            Range range = getRangeFromRequest(req);
            objectComponent = registrar.getObjectComponent(objectId, jsonPointer, metadata, range.getStart(), range.getEnd());
            if (objectComponent == null) {
                ServletErrorUtil.notFound(resp, "Missing data at jsonPointer in object");
            } else if (objectComponent instanceof ObjectComponentJson) {
                JsonNode jsonNode = ((ObjectComponentJson) objectComponent).json;
                if (isWantText) {
                    writeText(resp, jsonNode, objectComponent.mediaType);
                } else {
                    PrintWriter w = resp.getWriter();
                    
                    String json =  jsonNode.toString();
                    if (isWantXml) {
                        String xml = jsonToXml(json);
                        w.write(xml);  
                    } else {
                        w.write(json);                
                    }
                }
            } else {
                ObjectComponentStream objectComponentStream = (ObjectComponentStream) objectComponent;
                sendPayloadResponse(req, resp, metadata, objectComponentStream);
            }
        } catch (InvalidException e) { 
            ServletErrorUtil.internalServerError(resp); 
            logger.error("InvalidException in doGet", e);
        } catch (NoSuchDigitalObjectException e) { 
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (RepositoryException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Something went wrong getting " + objectId, e);
        } finally {
            if (objectComponent instanceof ObjectComponentStream && ((ObjectComponentStream) objectComponent).in != null) {
                try { ((ObjectComponentStream) objectComponent).in.close(); } catch (Exception e) { }
            }
        }
    }

    Range getRangeFromRequest(HttpServletRequest req) {
        if (req.getHeader("If-Range") != null) return new Range(null, null);
        String rangeHeader = req.getHeader("Range");
        if (rangeHeader == null) return new Range(null, null);
        if (!rangeHeader.startsWith("bytes=")) return new Range(null, null);
        if (rangeHeader.contains(",")) return new Range(null, null); // TODO handle multiple ranges
        String rangePart = rangeHeader.substring(6);
        String[] parts = rangePart.split("-");
        if (parts.length != 2) return new Range(null, null); // TODO handle bad format
        try {
            if (parts[0].isEmpty()) {
                return new Range(null, Long.parseLong(parts[1]));
            } else if (parts[1].isEmpty()) {
                return new Range(Long.parseLong(parts[0]), null);
            } else {
                return new Range(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
            }
        } catch (NumberFormatException e) {
            return new Range(null, null); // TODO handle bad format
        }
    }

    private void sendPayloadResponse(HttpServletRequest req, HttpServletResponse resp, boolean metadata, ObjectComponentStream objectComponentStream) throws IOException {
        String mediaType = objectComponentStream.mediaType;
        String filename = objectComponentStream.filename;
        if (metadata) {
            FileMetadataResponse metadataResponse = new FileMetadataResponse(filename, mediaType);
            PrintWriter writer = resp.getWriter();
            gson.toJson(metadataResponse, writer);
        } else {
            Long start = objectComponentStream.start;
            Long end = objectComponentStream.end;
            long size = objectComponentStream.size;
            if (start != null && end != null && (start >= size || end < start)) {
                resp.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                resp.setHeader("Content-Range", "bytes */" + size);
                return;
            }
            if (mediaType == null) {
                mediaType = "application/octet-stream";
            }
            String disposition = req.getParameter("disposition");
            if (disposition == null) disposition = "inline";
            resp.setHeader("Content-Disposition", ServletUtil.contentDispositionHeaderFor(disposition, filename));
            resp.setContentType(mediaType);
            if (start != null && end != null) {
                resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                resp.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
            }
            InputStream in = objectComponentStream.in;
            OutputStream out = resp.getOutputStream();
            IOUtils.copy(in, out);
        }
    }

    private void writeText(HttpServletResponse resp, JsonNode jsonNode, String mediaType) throws InvalidException, IOException {
        if (jsonNode.isValueNode()) {
            if (mediaType == null) mediaType = "text/plain";
            resp.setContentType(mediaType);
            PrintWriter w = resp.getWriter();
            w.write(jsonNode.asText());
        } else {
            ServletErrorUtil.badRequest(resp, "Requested json is not textual");
        }
    }
    
    private void doSearch(HttpServletRequest req, HttpServletResponse resp, String query) throws IOException {
        try {
            registrar.ensureIndexUpToDate();
            HttpSession session = req.getSession(false);
            String userId = null;
            if (session != null) {
                userId = (String) session.getAttribute("userId"); 
            }
            List<String> groupIds = registrar.getAclEnforcer().getGroupsForUser(userId);
            boolean excludeVersions = true;
            String restrictedQuery = QueryRestrictor.restrict(query, userId, groupIds, registrar.getDesign().authConfig, registrar.getRemoteAuthConfigs(), excludeVersions);
            String pageNumString = req.getParameter("pageNum");
            if (pageNumString == null) pageNumString = "0";
            String pageSizeString = req.getParameter("pageSize");
            if (pageSizeString == null) pageSizeString = "0";
            String sortFieldsString = req.getParameter("sortFields");
            int pageNum;
            int pageSize;
            try {
                pageNum = Integer.parseInt(pageNumString);
                pageSize = Integer.parseInt(pageSizeString);
            } catch (NumberFormatException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
                return;
            }
//            SearchResults results = registrar.search(restrictedQuery, pageNum, pageSize, sortFieldsString);
//            PrintWriter writer = resp.getWriter();
//            gson.toJson(results, writer);
            
            PrintWriter writer = resp.getWriter();
            registrar.search(restrictedQuery, pageNum, pageSize, sortFieldsString, writer);
        } catch (RepositoryException e) {
            if (e.getMessage().contains("Parse failure")) {
                ServletErrorUtil.badRequest(resp, "Query parse failure");
            } else {
                logger.error("Error in doSearch", e);
                ServletErrorUtil.internalServerError(resp);
            }
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        createOrUpdate(req, resp, false);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("application/x-www-form-urlencoded".equals(req.getContentType())) {
            doGet(req, resp);
        } else {
            createOrUpdate(req, resp, true);
        }
    }
    
    private void createOrUpdate(HttpServletRequest req, HttpServletResponse resp, boolean isCreate) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        //PathParser path = new PathParser(req.getPathInfo());
        String objectId = req.getPathInfo();
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        else objectId = null; 
        
        if (isCreate && objectId != null && !objectId.isEmpty()) {
            ServletErrorUtil.badRequest(resp, "Unexpected object id in POST");
            return;
        }
        if (!isCreate && (objectId == null || objectId.isEmpty())) {
            ServletErrorUtil.badRequest(resp, "Missing object id in PUT");
            return;
        }
        String objectType = null;
        if (isCreate) {
            objectType = req.getParameter("type");
            if (objectType == null || objectType.isEmpty()) {
                ServletErrorUtil.badRequest(resp, "Missing object type");
                return;
            }
            if (!registrar.isKnownType(objectType, null)) {
                ServletErrorUtil.badRequest(resp, "Unknown type " + objectType);
                return;
            }
        }
        List<String> payloadsToDelete = new ArrayList<String>();
        String[] payloadsToDeleteArray = req.getParameterValues("payloadToDelete");
        if (payloadsToDeleteArray != null) {
            for (String payloadName : payloadsToDeleteArray) {
                payloadsToDelete.add(payloadName);
            }
        }
        List<Payload> payloads = new ArrayList<Payload>();
        String handle = req.getParameter("handle");
        if (handle == null) {
            String suffix = req.getParameter("suffix");
            if (suffix != null) handle = registrar.getHandleForSuffix(suffix);
        }
        try {
            String jsonData = getJsonAndPayloadsFromRequest(req, payloads);
            for (Payload payload : payloads) {
                payloadsToDelete.remove(payload.name);
            }
            if (jsonData == null) {
                ServletErrorUtil.badRequest(resp, "Missing JSON");
                return;
            }
            DigitalObject dobj;
            HttpSession session = req.getSession(false);
            String userId = null;
            if (session != null) {
                userId = (String) session.getAttribute("userId");
            }
            if (isCreate) {
                dobj = registrar.writeJsonAndPayloadsIntoDigitalObjectIfValid(objectType, jsonData, payloads, handle, userId);
            } else {
                dobj = registrar.writeJsonAndPayloadsIntoDigitalObjectIfValidAsUpdate(objectId, jsonData, payloads, userId, payloadsToDelete);
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setHeader("Location", StringUtils.encodeURLPath("/objects/" + dobj.getHandle()));
            resp.setHeader("X-Schema", dobj.getAttribute("type"));
            boolean isFull = ServletUtil.getBooleanParameter(req, "full");
            PrintWriter p = resp.getWriter();
            if (isFull) {
                ContentPlusMeta contentPlusMeta = RegistrarService.createContentPlusMeta(dobj);
                String contentPlusMetaJson = gson.toJson(contentPlusMeta);
                p.write(contentPlusMetaJson);
            } else {
                p.write(dobj.getAttribute(JSON));
            }
        } catch (NoSuchDigitalObjectException e) {
            ServletErrorUtil.notFound(resp, "Missing object");
        } catch (CreationException e) {
            if (e.getMessage() != null) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
                logger.error("CreationException in doPost", e);
            } else if (handle != null) {
                ServletErrorUtil.badRequest(resp, "Object " + handle + " already exists");
                logger.error("CreationException in doPost", e);
            } else {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected CreationException in doPost", e);
            }
        } catch (InternalException e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("InternalException in doPost", e);
        } catch (InvalidException invalidException) {
            ServletErrorUtil.badRequest(resp, invalidException.getMessage());
            logger.error("InvalidException in doPost", invalidException);
        } catch (Exception e) {
            ServletErrorUtil.internalServerError(resp);
            logger.error("Unexpected exception in doPost", e);
        } finally {
            for (Payload payload : payloads) {
                try { payload.in.close(); } catch (Exception e) { }
            }
        }
    }

    private String getJsonAndPayloadsFromRequest(HttpServletRequest req, List<Payload> payloads) throws IOException, ServletException {
        if (isMultipart(req)) {
            String jsonData = null;
            Collection<Part> parts = req.getParts();
            for(Part part : parts) {
                String partName = part.getName();
                String filename = getFileName(part.getHeader("Content-Disposition"));
                if (JSON.equals(partName) && filename == null) {
                    jsonData = getPartAsString(part);
                } else {
                    String payloadName = partName;
                    if (filename != null) {
                        // form-data without filename treated as parameters, so does not generate a payload
                        // TODO consider whether to allow empty filename vs missing filename
                        Payload payload = new Payload(payloadName, part.getInputStream(), part.getContentType(), filename);
                        payloads.add(payload);
                    }
                }
            }
            return jsonData;
        } else if (isForm(req) && "POST".equals(req.getMethod())) {
            return req.getParameter(JSON);
        } else if (isForm(req)) {
            return getJsonFromFormData(StreamUtil.readFully(req.getReader()));
        } else {
            return StreamUtil.readFully(req.getReader());
        }
    }
    
    static String getJsonFromFormData(String data) {
        String[] strings = data.split("&");
        for (String string : strings) {
            int equals = string.indexOf('=');
            if (equals < 0) {
                if (JSON.equals(StringUtils.decodeURL(string))) return "";
            } else {
                String name = StringUtils.decodeURL(string.substring(0, equals));
                if (JSON.equals(name)) {
                    String value = StringUtils.decodeURL(string.substring(equals + 1));
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isMultipart(HttpServletRequest req) {
        String contentType = req.getContentType();
        if (contentType == null) return false;
        contentType = contentType.toLowerCase(Locale.ENGLISH);
        return contentType.startsWith("multipart/form-data");
    }

    private static boolean isForm(HttpServletRequest req) {
        String contentType = req.getContentType();
        if (contentType == null) return false;
        contentType = contentType.toLowerCase(Locale.ENGLISH);
        return contentType.startsWith("application/x-www-form-urlencoded");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String objectId = req.getPathInfo();
        if (objectId != null && !objectId.isEmpty()) objectId = objectId.substring(1);
        if (objectId == null || objectId.isEmpty()) {
            ServletErrorUtil.badRequest(resp, "Missing object id in DELETE");
            return;
        }
        String jsonPointer = req.getParameter("jsonPointer");
        String payloadName = req.getParameter("payload");
        if (jsonPointer != null && payloadName != null) {
            ServletErrorUtil.badRequest(resp, "DELETE specified both jsonPointer and payload");
            return;
        }
        if ("".equals(jsonPointer)) {
            ServletErrorUtil.badRequest(resp, "Cannot delete empty json pointer");
            return;
        }
        if (jsonPointer == null && payloadName == null) {
            try {
                registrar.delete(objectId);
            } catch (NoSuchDigitalObjectException e) {
                ServletErrorUtil.notFound(resp, "Missing object");
            } catch (RepositoryException e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected exception in doDelete", e);
            }
        } else {
            try {
                String userId = null;
                HttpSession session = req.getSession(false);
                if (session != null) {
                    userId = (String) session.getAttribute("userId");
                }
                if (jsonPointer != null) {
                    registrar.deleteJsonPointer(objectId, jsonPointer, userId);
                }
                if (payloadName != null) {
                    registrar.deletePayload(objectId, payloadName, userId);
                }
            } catch (NoSuchDigitalObjectException e) {
                ServletErrorUtil.notFound(resp, "Missing object");
            } catch (RepositoryException e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected exception in doDelete", e);
            } catch (InvalidException e) {
                ServletErrorUtil.badRequest(resp, e.getMessage());
                logger.error("InvalidException in doDelete", e);
            } catch (Exception e) {
                ServletErrorUtil.internalServerError(resp);
                logger.error("Unexpected exception in doDelete", e);
            }
        }
    }
    
    private static String getPartAsString(Part part) throws IOException {
        InputStream in = part.getInputStream();
        String result = IOUtils.toString(in, "UTF-8").trim();
        in.close();
        return result;
    }
    
    static String getFileName(String contentDispositionHeader) {
        if (contentDispositionHeader == null) return null;
        int index = contentDispositionHeader.indexOf(';');
        if (index < 0) return null;
        String firstPart = contentDispositionHeader.substring(0, index);
        if (firstPart.contains("=") || firstPart.contains(",") || firstPart.contains("\"")) return null;
        String filename = null;
        String filenameStar = null;
        while (index >= 0) {
            int nameStart = index + 1;
            int equals = contentDispositionHeader.indexOf('=', nameStart);
            if (equals < 0) return null;
            String name = contentDispositionHeader.substring(nameStart, equals).trim().toLowerCase(Locale.ENGLISH);
            if (name.contains(",") || name.contains(";")) return null;
            int valueStart = skipWhitespace(contentDispositionHeader, equals + 1);
            if (valueStart >= contentDispositionHeader.length()) return null;
            char ch = contentDispositionHeader.charAt(valueStart);
            if (ch == ';') return null;
            boolean quoted = ch == '"';
            if (quoted) valueStart++;
            ch = ';';
            int valueEnd = valueStart;
            while (valueEnd < contentDispositionHeader.length()) {
                ch = contentDispositionHeader.charAt(valueEnd);
                if (quoted && ch == '"') break;
                if (!quoted && ch == ';') break;
                if (!quoted && (ch == ',' || ch == ' ')) return null;
                if (quoted && ch == '\\') {
                    valueEnd++;
                    if (valueEnd >= contentDispositionHeader.length()) break;
                }
                valueEnd++;
            }
            if (quoted && ch != '"') return null;
            if (quoted) {
                if (unexpectedCharactersAfterQuotedString(contentDispositionHeader, valueEnd)) return null;
            }
            if ("filename".equals(name) || "filename*".equals(name)) {
                String value = contentDispositionHeader.substring(valueStart, valueEnd).trim();
                if (quoted) {
                    value = unescapeQuotedStringContents(value);
                }
                if ("filename*".equals(name)) {
                    if (quoted) return null;
                    if (filenameStar != null) continue;
                    String decoding = rfc5987Decode(value);
                    if (decoding != null) {
                        filenameStar = decoding;
                    }
                } else {
                    if (filename != null) return null;
                    filename = value;
                }
            }
            index = contentDispositionHeader.indexOf(';', valueEnd);
        }
        if (filenameStar != null) filename = filenameStar;
        filename = stripPath(filename);
        return filename;
    }

    private static int skipWhitespace(String contentDispositionHeader, int index) {
        while (index < contentDispositionHeader.length()) {
            char ch = contentDispositionHeader.charAt(index);
            if (!Character.isWhitespace(ch)) break;
            index++;
        }
        return index;
    }
    
    private static boolean unexpectedCharactersAfterQuotedString(String contentDispositionHeader, int index) {
        int semicolon = contentDispositionHeader.indexOf(';', index);
        String remainder;
        if (semicolon < 0) {
            remainder = contentDispositionHeader.substring(index + 1);
        } else {
            remainder = contentDispositionHeader.substring(index + 1, semicolon);
        }
        return !remainder.trim().isEmpty(); 
    }
    
    private static String unescapeQuotedStringContents(String value) {
        StringBuilder sb = new StringBuilder(value);
        int escape = sb.indexOf("\\");
        while (escape >= 0) {
            sb.replace(escape, escape + 2, sb.substring(escape + 1, escape + 2));
            escape = sb.indexOf("\\", escape + 1);
        }
        value = sb.toString();
        return value;
    }

    private static String rfc5987Decode(String value) {
        int apos = value.indexOf('\'');
        int apos2 = -1;
        if (apos > 0) apos2 = value.indexOf('\'', apos + 1);
        if (apos2 < 0) return null;
        String enc = value.substring(0, apos);
        value = value.substring(apos2 + 1);
        try {
            return URLDecoder.decode(value.replaceAll("\\+", "%2B"), enc);
        } catch (UnsupportedEncodingException e) {
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    private static String stripPath(String filename) {
        if (filename != null) {
            int lastSlash = filename.lastIndexOf('/');
            int lastBackslash = filename.lastIndexOf('\\');
            if (lastSlash > lastBackslash) filename = filename.substring(lastSlash + 1);
            else if (lastBackslash > lastSlash) filename = filename.substring(lastBackslash + 1);
        }
        return filename;
    }
}
