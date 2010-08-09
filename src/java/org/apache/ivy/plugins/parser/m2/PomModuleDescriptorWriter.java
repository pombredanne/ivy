/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.plugins.parser.m2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.util.ConfigurationUtils;
import org.apache.ivy.util.StringUtils;

public final class PomModuleDescriptorWriter {
    // used to ease tests only
    private static boolean addIvyVersion = true;
    static void setAddIvyVersion(boolean addIvyVersion) {
        PomModuleDescriptorWriter.addIvyVersion = addIvyVersion;
    }

    private PomModuleDescriptorWriter() {
    }
    
    public static void write(ModuleDescriptor md, File output, PomWriterOptions options)
            throws IOException {
        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }
        PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output),
                "UTF-8"));
        try {
            out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            if (options.getLicenseHeader() != null) {
                out.print(options.getLicenseHeader());
            }
            out.println("<!--"); 
            out.println("   Apache Maven 2 POM generated by Apache Ivy"); 
            out.println("   " + Ivy.getIvyHomeURL());
            if (addIvyVersion) {
                out.println("   Apache Ivy version: " + Ivy.getIvyVersion() 
                    + " " + Ivy.getIvyDate());
            }
            out.println("-->"); 
            out.println("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" "
                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            out.println("    xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
                    + "http://maven.apache.org/maven-v4_0_0.xsd\">\n");
            out.println("  <modelVersion>4.0.0</modelVersion>");
            printModuleId(md, out);
            printDependencies(md, out, options);
            out.println("</project>");
        } finally {
            out.close();
        }
    }

    private static void printModuleId(ModuleDescriptor md, PrintWriter out) {
        ModuleRevisionId mrid = md.getModuleRevisionId();
        out.println("  <groupId>" + mrid.getOrganisation() + "</groupId>");
        out.println("  <artifactId>" + mrid.getName() + "</artifactId>");
        
        String type;
        
        Artifact artifact = findArtifact(md);
        if (artifact == null) {
            // no suitable artifact found, default to 'pom'
            type = "pom";
        } else {
            type = artifact.getType();
        }

        out.println("  <packaging>" + type + "</packaging>");
        if (mrid.getRevision() != null) {
            out.println("  <version>" + mrid.getRevision() + "</version>");
        }
        if (md.getHomePage() != null) {
            out.println("  <url>" + md.getHomePage() + "</url>");
        }
    }
    
    /**
     * Returns the first artifact with the correct name and without a classifier.
     */
    private static Artifact findArtifact(ModuleDescriptor md) {
        Artifact[] artifacts = md.getAllArtifacts();
        for (int i = 0; i < artifacts.length; i++) {
            if (artifacts[i].getName().equals(md.getModuleRevisionId().getName())
                    && artifacts[i].getAttribute("classifier") == null) {
                return artifacts[i];
            }
        }
        
        return null;
    }

    private static void printDependencies(ModuleDescriptor md, PrintWriter out, 
            PomWriterOptions options) {
        DependencyDescriptor[] dds = getDependencies(md, options);
        if (dds.length > 0) {
            ConfigurationScopeMapping mapping = options.getMapping();
            out.println("  <dependencies>");
            for (int i = 0; i < dds.length; i++) {
                ModuleRevisionId mrid = dds[i].getDependencyRevisionId();
                out.println("    <dependency>");
                out.println("      <groupId>" + mrid.getOrganisation() + "</groupId>");
                out.println("      <artifactId>" + mrid.getName() + "</artifactId>");
                out.println("      <version>" + mrid.getRevision() + "</version>");
                String scope = mapping.getScope(dds[i].getModuleConfigurations());
                if (scope != null) {
                    out.println("      <scope>" + scope + "</scope>");
                }
                if (mapping.isOptional(dds[i].getModuleConfigurations())) {
                    out.println("      <optional>true</optional>");
                }
                out.println("    </dependency>");
            }
            out.println("  </dependencies>");
        }
    }
    
    private static DependencyDescriptor[] getDependencies(ModuleDescriptor md, 
            PomWriterOptions options) {
        String[] confs = ConfigurationUtils.replaceWildcards(options.getConfs(), md);

        List result = new ArrayList();
        DependencyDescriptor[] dds = md.getDependencies();
        for (int i = 0; i < dds.length; i++) {
            String[] depConfs = dds[i].getDependencyConfigurations(confs);
            if ((depConfs != null) && (depConfs.length > 0)) {
                result.add(dds[i]);
            }
        }
        
        return (DependencyDescriptor[]) result.toArray(new DependencyDescriptor[result.size()]);
    }
    
    public static final ConfigurationScopeMapping DEFAULT_MAPPING 
        = new ConfigurationScopeMapping(new HashMap() {
            {
                put("compile, runtime", "compile");
                put("runtime", "runtime");
                put("provided", "provided");
                put("test", "test");
                put("system", "system");
            }
        });

    public static class ConfigurationScopeMapping {
        private Map/*<String,String>*/ scopes;
        
        public ConfigurationScopeMapping(Map/*<String,String>*/ scopesMapping) {
            this.scopes = new HashMap(scopesMapping);
        }

        /**
         * Returns the scope mapped to the given configuration array.
         * 
         * @param confs the configurations for which the scope should be returned
         * @return the scope to which the conf is mapped
         */
        public String getScope(String[] confs) {
            return (String) scopes.get(StringUtils.join(confs, ", "));
        }
        public boolean isOptional(String[] confs) {
            return getScope(confs) == null;
        }
    }
}
