/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;

public class InvalidException extends Exception {
    ProcessingReport report;
    
    public InvalidException() {
    }
    
    public InvalidException(Throwable cause) {
        super(cause);
    }
    
    public InvalidException(ProcessingReport report) {
        super(messageFromReport(report));
        this.report = report;
    }
    
    public InvalidException(ProcessingReport report, Throwable cause) {
        super(messageFromReport(report), cause);
        this.report = report;
    }
    
    public InvalidException(String message) {
        super(message);
    }
    
    public InvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessingReport getReport() {
        return report;
    }
    
    private static String messageFromReport(ProcessingReport report) {
        for (ProcessingMessage msg : report) {
            if (msg.getLogLevel() == LogLevel.ERROR || msg.getLogLevel() == LogLevel.FATAL) {
                return msg.getMessage();
            }
        }
        return "Unexpected processing report";
    }
}
