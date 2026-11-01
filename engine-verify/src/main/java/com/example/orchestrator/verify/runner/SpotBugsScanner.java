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
 * SpotBugs scanner.
 */
@Component
public class SpotBugsScanner implements Scanner {

    @Override
    public String name() {
        return "spotbugs";
    }

    @Override
    public String command(Path workspacePath) {
        return "mvn spotbugs:spotbugs";
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        
        // SpotBugs outputs XML to target/spotbugsXml.xml by default
        // For now, parse from stdout/stderr if available
        // In production, would read the XML file directly
        
        String combined = stdout + "\n" + stderr;
        if (combined.contains("<BugCollection")) {
            try {
                JAXBContext context = JAXBContext.newInstance(BugCollection.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                BugCollection collection = (BugCollection) unmarshaller.unmarshal(new StringReader(combined));
                
                for (BugInstance bug : collection.getBugInstances()) {
                    findings.add(new Finding(
                        "spotbugs-" + bug.getInstanceHash(),
                        "spotbugs",
                        bug.getType(),
                        mapSeverity(bug.getPriority()),
                        bug.getMessage(),
                        bug.getClassName().replace('.', '/') + ".java",
                        bug.getPrimaryLine(),
                        "",
                        "",
                        java.util.Map.of("category", bug.getCategory(), "patternId", bug.getPatternId())
                    ));
                }
            } catch (JAXBException e) {
                // Ignore parsing errors
            }
        }
        
        return findings;
    }
    
    private Severity mapSeverity(int priority) {
        return switch (priority) {
            case 1 -> com.example.orchestrator.verify.Severity.CRITICAL;
            case 2 -> com.example.orchestrator.verify.Severity.HIGH;
            default -> com.example.orchestrator.verify.Severity.MEDIUM;
        };
    }
    
    // JAXB classes for SpotBugs XML
    @XmlRootElement(name = "BugCollection")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BugCollection {
        @XmlElement(name = "BugInstance")
        private List<BugInstance> bugInstances = new ArrayList<>();
        
        public List<BugInstance> getBugInstances() { return bugInstances; }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BugInstance {
        @XmlAttribute(name = "instanceHash")
        private String instanceHash;
        
        @XmlAttribute(name = "type")
        private String type;
        
        @XmlAttribute(name = "priority")
        private int priority;
        
        @XmlAttribute(name = "category")
        private String category;
        
        @XmlAttribute(name = "patternId")
        private String patternId;
        
        @XmlAttribute(name = "primaryLine")
        private int primaryLine;
        
        @XmlAttribute(name = "class")
        private String className;
        
        @XmlElement(name = "Message")
        private MessageElement messageElement;
        
        public String getInstanceHash() { return instanceHash; }
        public String getType() { return type; }
        public int getPriority() { return priority; }
        public String getCategory() { return category; }
        public String getPatternId() { return patternId; }
        public int getPrimaryLine() { return primaryLine; }
        public String getClassName() { return className; }
        public String getMessage() { return messageElement != null ? messageElement.getText() : ""; }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MessageElement {
        @XmlValue
        private String text;
        
        public String getText() { return text; }
    }
}