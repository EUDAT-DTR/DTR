package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.util.StringUtils;

public class LocBuilder {

    public static String createLocFor(HandleMintingConfig config, DigitalObject dobj, String type, JsonNode dataNode) throws RepositoryException {
        String baseUri = config.baseUri;
        String id = dobj.getHandle();
        StringBuilder sb = new StringBuilder();
        sb.append("<locations>\n");
        List<LinkConfig> links = getConfigForObjectType(config, type);
        for (LinkConfig link : links) {
            String href = "";
            String weight = "0";
            if (link.primary) {
                weight = "1";
            }
            String view = "";
            if ("json".equals(link.type)) {
                href = baseUri + "objects/" + StringUtils.encodeURLPath(id);
                view = "json";
                String line = "<location href=\""+href+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
                sb.append(line);
            } else if ("ui".equals(link.type)) {
                href = baseUri + "#objects/" + StringUtils.encodeURLPath(id);
                view = "ui";
                String line = "<location href=\""+href+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
                sb.append(line);
            } else if ("payload".equals(link.type)) {
                if (link.all != null && link.all == true) {
                    try {
                        List<DataElement> elements = dobj.getDataElements();
                        for (DataElement element : elements) {
                            String line = getLocationForPayload(element, baseUri, weight, id);
                            sb.append(line);
                        }
                    } catch (RepositoryException e) {
                        e.printStackTrace();
                    }
                } else if (link.specific != null) {
                    try {
                        DataElement element = dobj.getDataElement(link.specific);
                        String line = getLocationForPayload(element, baseUri, weight, id);
                        sb.append(line);
                    } catch (RepositoryException e) {
                        e.printStackTrace();
                    }
                }
            } else if ("url".equals(link.type)) {
                if (link.specific != null) {
                    JsonNode urlNode = JsonUtil.getJsonAtPointer(link.specific, dataNode);
                    String url = urlNode.asText();
                    view = link.specific;
                    String line = "<location href=\""+url+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
                    sb.append(line);
                }
                //TODO consider all properties with format url
            } else {
                continue;
            }
        }
        sb.append("</locations>");
        return sb.toString();
    }
    
    public static String getLocationForPayload(DataElement element, String baseUri, String weight, String id) {
        String payloadName = element.getName();
        String href = baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?payload=" + StringUtils.encodeURLPath(payloadName);
        String view = payloadName;
        String line = "<location href=\""+href+"\" weight=\""+weight+"\" view=\""+view+"\" />\n";
        return line;
    }
    
    public static List<LinkConfig> getConfigForObjectType(HandleMintingConfig config, String type) {
        if (config.schemaSpecificLinks == null) {
            return config.defaultLinks;
        } else {
            List<LinkConfig> result = config.schemaSpecificLinks.get(type);
            if (result == null) {
                return config.defaultLinks;
            } else {
                return result;
            }
        }
    }
}
