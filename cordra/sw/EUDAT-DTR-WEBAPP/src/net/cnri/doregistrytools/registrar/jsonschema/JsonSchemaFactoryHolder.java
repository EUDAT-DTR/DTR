/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.NodeType;
import com.github.fge.jsonschema.cfg.ValidationConfiguration;
import com.github.fge.jsonschema.cfg.ValidationConfigurationBuilder;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.keyword.syntax.checkers.helpers.TypeOnlySyntaxChecker;
import com.github.fge.jsonschema.core.processing.Processor;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.format.AbstractFormatAttribute;
import com.github.fge.jsonschema.keyword.validator.AbstractKeywordValidator;
import com.github.fge.jsonschema.keyword.validator.KeywordValidator;
import com.github.fge.jsonschema.library.DraftV4Library;
import com.github.fge.jsonschema.library.Keyword;
import com.github.fge.jsonschema.library.KeywordBuilder;
import com.github.fge.jsonschema.library.LibraryBuilder;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.processors.data.FullData;
import com.github.fge.msgsimple.bundle.MessageBundle;

public class JsonSchemaFactoryHolder {
    static JsonSchemaFactory INSTANCE;

    static {
        LibraryBuilder lib = DraftV4Library.get().thaw();
//        lib.addFormatAttribute("file", new RecordingFormatAttribute("file"));
        lib.addKeyword(recordingKeyword(Constants.REPOSITORY_SCHEMA_KEYWORD, RegistrarKeywordValidator.class));
        ValidationConfigurationBuilder vcb = ValidationConfiguration.newBuilder();
        vcb.setDefaultLibrary("http://doregistry.org/schema", lib.freeze());   
        INSTANCE = JsonSchemaFactory.newBuilder().setValidationConfiguration(vcb.freeze()).freeze();
    }
    
    public static JsonSchemaFactory getJsonSchemaFactory() {
//      return JsonSchemaFactory.byDefault();
      return JsonSchemaFactoryHolder.INSTANCE;
    }
  
    private static Keyword recordingKeyword(String keyword, Class<? extends KeywordValidator> klass) {
        KeywordBuilder kb = Keyword.newBuilder(keyword);
        // if we need the whole schema chunk, this should be withIdentityDigester, if only the keyword, withSimpleDigester
        kb.withIdentityDigester(NodeType.STRING, NodeType.values()); // ignored except on data json of these types
        kb.withSyntaxChecker(new TypeOnlySyntaxChecker(keyword, NodeType.OBJECT)); // attribute value of keyword in schema json must be this type or validation fails
        kb.withValidatorClass(klass);
        return kb.freeze();
    }
    
    private static class RecordingFormatAttribute extends AbstractFormatAttribute {
        public RecordingFormatAttribute(String format) {
            super(format, NodeType.STRING); // ignored except on data json of these types
        }
        
        @Override
        public void validate(ProcessingReport report, MessageBundle bundle, FullData data) throws ProcessingException {
            ProcessingMessage msg = newMsg(data, bundle, "net.cnri.message");
            report.info(msg);
        }
    }
    
    public static class RecordingKeywordValidator extends AbstractKeywordValidator {
        final JsonNode attribute;
        
        public RecordingKeywordValidator(String keyword, JsonNode node) {
            super(keyword);
            this.attribute = node.get(keyword);
        }
        
        @Override
        public void validate(Processor<FullData,FullData> processor, ProcessingReport report, MessageBundle bundle, FullData data) throws ProcessingException {
            ProcessingMessage msg = newMsg(data, bundle, "net.cnri.message");
            msg.put("attribute", attribute);
            report.info(msg);
        }
        
        @Override
        public String toString() {
            return "RecordingKeywordValidator(" + keyword + ")";
        }
    }
    
    public static class RegistrarKeywordValidator extends RecordingKeywordValidator {
        public RegistrarKeywordValidator(JsonNode node) {
            super(Constants.REPOSITORY_SCHEMA_KEYWORD, node);
        }
    }
}
