/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.List;
import java.util.Map;

public class Relationships {
    List<Node> nodes;
    List<Edge> edges;
    Map<String, SearchResult> results; 
    
    public Relationships() {
    }

    public Relationships(List<Node> nodes, List<Edge> edges, Map<String, SearchResult> results) {
        this.nodes = nodes;
        this.edges = edges;
        this.results = results;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }
    
    
}
