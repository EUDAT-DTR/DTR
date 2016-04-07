/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.main.JsonSchema;

public class SchemaAndNode {
    public JsonSchema schema;
    public JsonNode schemaNode;

    public SchemaAndNode(JsonSchema schema, JsonNode schemaNode) {
        this.schema = schema;
        this.schemaNode = schemaNode;
    }
}
