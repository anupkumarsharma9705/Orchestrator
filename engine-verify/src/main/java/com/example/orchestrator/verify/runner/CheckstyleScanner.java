package com.example.orchestrator.verify.runner;

import com.example.orchestrator.verify.Finding;
import com.example.orchestrator.verify.Severity;
import org.springframework.stereotype.Component;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.*;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checkstyle scanner.
 */
@Component
public class CheckstyleScanner implements Scanner {

    @Override
    public String name() {
        return "checkstyle";
    }

    @Override
    public String command(Path workspacePath) {
        return "mvn checkstyle:check";
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        
        String combined = stdout + "\n" + stderr;
        if (combined.contains("<checkstyle")) {
            try {
                JAXBContext context = JAXBContext.newInstance(CheckstyleReport.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                CheckstyleReport report = (CheckstyleReport) unmarshaller.unmarshal(new StringReader(combined));
                
                for (FileResult file : report.getFiles()) {
                    for (ErrorResult error : file.getErrors()) {
                        findings.add(new Finding(
                            "checkstyle-" + System.nanoTime(),
                            "checkstyle",
                            error.getSource(),
                            mapSeverity(error.getSeverity()),
                            error.getMessage(),
                            file.getName(),
                            error.getLine(),
                            "",
                            "",
                            java.util.Map.of("column", error.getColumn())
                        ));
                    }
                }
            } catch (JAXBException e) {
                // Ignore parsing errors
            }
        }
        
        return findings;
    }
    
    private Severity mapSeverity(String severity) {
        return switch (severity.toLowerCase()) {
            case "error" -> com.example.orchestrator.verify.Severity.HIGH;
            case "warning" -> com.example.orchestrator.verify.Severity.MEDIUM;
            default -> com.example.orchestrator.verify.Severity.LOW;
        };
    }
    
    // JAXB classes for Checkstyle XML
    @XmlRootElement(name = "checkstyle")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CheckstyleReport {
        @XmlElement(name = "file")
        private List<FileResult> files = new ArrayList<>();
        
        public List<FileResult> getFiles() { return files; }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FileResult {
        @XmlAttribute(name = "name")
        private String name;
        
        @XmlElement(name = "error")
        private List<ErrorResult> errors = new ArrayList<>();
        
        public String getName() { return name; }
        public List<ErrorResult> getErrors() { return errors; }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ErrorResult {
        @XmlAttribute(name = "line")
        private int line;
        
        @XmlAttribute(name = "column")
        private int column;
        
        @XmlAttribute(name = "severity")
        private String severity;
        
        @XmlAttribute(name = "source")
        private String source;
        
        @XmlValue
        private String message;
        
        public int getLine() { return line; }
        public int getColumn() { return column; }
        public String getSeverity() { return severity; }
        public String getSource() { return source; }
        public String getMessage() { return message; }
    }
}