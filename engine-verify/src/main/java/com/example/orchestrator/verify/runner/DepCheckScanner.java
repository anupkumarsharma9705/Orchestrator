package com.example.orchestrator.verify.runner;

import com.example.orchestrator.verify.Finding;
import com.example.orchestrator.verify.Severity;
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
 * OWASP Dependency-Check scanner.
 */
@Component
public class DepCheckScanner implements Scanner {

    @Override
    public String name() {
        return "depcheck";
    }

    @Override
    public String command(Path workspacePath) {
        return "mvn dependency-check:check";
    }

    @Override
    public List<Finding> parseOutput(Path workspacePath, String stdout, String stderr) {
        List<Finding> findings = new ArrayList<>();
        
        String combined = stdout + "\n" + stderr;
        if (combined.contains("<Report")) {
            try {
                JAXBContext context = JAXBContext.newInstance(Report.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                Report report = (Report) unmarshaller.unmarshal(new StringReader(combined));
                
                for (Dependency dep : report.getDependencies()) {
                    for (Vulnerability vuln : dep.getVulnerabilities()) {
                        findings.add(new Finding(
                            "depcheck-" + vuln.getName(),
                            "depcheck",
                            vuln.getName(),
                            mapSeverity(vuln.getSeverity()),
                            vuln.getDescription(),
                            dep.getFilePath(),
                            0,
                            "",
                            "",
                            java.util.Map.of(
                                "dependency", dep.getFileName(),
                                "cwe", vuln.getCwe(),
                                "cvss", vuln.getCvssScore()
                            )
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
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> com.example.orchestrator.verify.Severity.CRITICAL;
            case "HIGH" -> com.example.orchestrator.verify.Severity.HIGH;
            case "MEDIUM" -> com.example.orchestrator.verify.Severity.MEDIUM;
            case "LOW" -> com.example.orchestrator.verify.Severity.LOW;
            default -> com.example.orchestrator.verify.Severity.LOW;
        };
    }
    
    // JAXB classes for Dependency-Check XML
    @XmlRootElement(name = "Report")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Report {
        @XmlElement(name = "Dependency")
        private List<Dependency> dependencies = new ArrayList<>();
        
        public List<Dependency> getDependencies() { return dependencies; }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Dependency {
        @XmlAttribute(name = "filePath")
        private String filePath;
        
        @XmlAttribute(name = "fileName")
        private String fileName;
        
        @XmlElement(name = "Vulnerability")
        private List<Vulnerability> vulnerabilities = new ArrayList<>();
        
        public String getFilePath() { return filePath; }
        public String getFileName() { return fileName; }
        public List<Vulnerability> getVulnerabilities() { return vulnerabilities; }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Vulnerability {
        @XmlAttribute(name = "name")
        private String name;
        
        @XmlAttribute(name = "severity")
        private String severity;
        
        @XmlElement(name = "Description")
        private String description;
        
        @XmlElement(name = "Cwe")
        private String cwe;
        
        @XmlElement(name = "CvssScore")
        private double cvssScore;
        
        public String getName() { return name; }
        public String getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getCwe() { return cwe; }
        public double getCvssScore() { return cvssScore; }
    }
}