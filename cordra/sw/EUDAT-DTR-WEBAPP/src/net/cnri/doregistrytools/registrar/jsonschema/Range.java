package net.cnri.doregistrytools.registrar.jsonschema;

public class Range {
    private final Long start;
    private final Long end;
    
    public Range(Long start, Long end) {
        this.start = start;
        this.end = end;
    }

    public Long getStart() {
        return start;
    }

    public Long getEnd() {
        return end;
    }
}
