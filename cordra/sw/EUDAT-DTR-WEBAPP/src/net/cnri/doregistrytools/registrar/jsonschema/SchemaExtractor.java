/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;

public class SchemaExtractor {
    
    public static Map<String, JsonNode> extract(ProcessingReport report, JsonNode schema) {
        Map<String, JsonNode> schemas = new HashMap<String,JsonNode>();
        for (ProcessingMessage msg : report) {
            if (msg.getLogLevel() == LogLevel.INFO && "net.cnri.message".equals(msg.getMessage())) {
                JsonNode node = msg.asJson();
                String keyword = node.get("keyword").asText(null);
                if (keyword == null) continue;
                if ("format".equals(keyword)) {
                    keyword = node.get("attribute").asText(null);
                    if (keyword == null) continue;
                }
                String pointer = node.get("instance").get("pointer").asText(null);
                if (pointer == null) continue;
                String schemaPointer = node.get("schema").get("pointer").asText(null);
                if (schemaPointer == null) continue;
                JsonNode subSchema = JsonUtil.getJsonAtPointer(schemaPointer, schema);
                schemas.put(pointer, subSchema);
            }
        }
        return schemas;
    }

}
