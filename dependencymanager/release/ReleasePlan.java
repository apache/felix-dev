/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

/**
 * Proposes which Dependency Manager bundles to release, and with which versions.
 * <p>
 * Run it with <code>mvn -N exec:exec@release-plan</code> in the dependencymanager directory.
 * <p>
 * A bundle (a module that tags itself when a release is performed, see the parent pom) needs a new release when
 * something other than its pom, its tests and its documentation changed since the git tag of its latest release
 * (<code>artifactId-version</code>). The bundles that did not change are put back to the version they already have,
 * which makes the release plugin tag and deploy only the changed ones, see the release profile in the parent pom.
 * <p>
 * Versions follow the Felix convention: a development version has an odd patch number (5.0.1-SNAPSHOT) and is
 * released with the next even one (5.0.2).
 */
public class ReleasePlan {
    private static final String GROUP_ID = "org.apache.felix";

    /** What a module is, as far as the plan is concerned. */
    private static class Module {
        final String dir;
        final String artifactId;
        final String version;
        final boolean bundle;
        final boolean bundlePackaging;
        String lastRelease;
        List<String> changes = new ArrayList<>();
        boolean pomChanged;

        Module(String dir, String artifactId, String version, boolean bundle, boolean bundlePackaging) {
            this.dir = dir;
            this.artifactId = artifactId;
            this.version = version;
            this.bundle = bundle;
            this.bundlePackaging = bundlePackaging;
        }

        boolean changed() {
            return lastRelease == null || !changes.isEmpty();
        }
    }

    public static void main(String[] args) throws Exception {
        File base = new File(".").getCanonicalFile();
        Document parentPom = parse(new File(base, "pom.xml"));
        Module parent = new Module(".", xpath(parentPom, "/project/artifactId"), xpath(parentPom, "/project/version"), false, false);

        List<Module> modules = new ArrayList<>();
        for (String dir : xpaths(parentPom, "/project/modules/module")) {
            Document pom = parse(new File(base, dir + "/pom.xml"));
            String version = xpath(pom, "/project/version");
            if (version.isEmpty()) {
                version = xpath(pom, "/project/parent/version");
            }
            boolean bundle = !xpath(pom, "//plugin[artifactId='maven-antrun-plugin']/artifactId").isEmpty();
            boolean bundlePackaging = xpath(pom, "/project/packaging").equals("bundle")
                && !xpath(pom, "/project/properties/maven.deploy.skip").equals("true");
            modules.add(new Module(dir, xpath(pom, "/project/artifactId"), version, bundle, bundlePackaging));
        }

        for (Module module : modules) {
            if (module.bundle) {
                module.lastRelease = lastRelease(base, module.artifactId);
                if (module.lastRelease != null) {
                    module.changes = changes(base, module);
                    module.pomChanged = pomChanged(base, module);
                }
            }
        }

        print(parent, modules);
    }

    private static void print(Module parent, List<Module> modules) {
        System.out.println();
        System.out.println("Dependency Manager release plan");
        System.out.println("===============================");
        System.out.println();
        boolean anyChanged = false;
        for (Module module : modules) {
            if (module.bundlePackaging && !module.bundle) { // a bundle that is released
                System.out.println("  WARNING    " + module.artifactId + " is a bundle, but it does not list the maven-antrun-plugin"
                    + ": it is always released and never tagged, see the parent pom");
            }
            if (!module.bundle) {
                continue;
            }
            if (module.lastRelease == null) {
                System.out.println("  CHANGED    " + module.artifactId + ": never released");
                anyChanged = true;
            } else if (module.changed()) {
                System.out.println("  CHANGED    " + module.artifactId + ": " + module.changes.size() + " file(s) since "
                    + module.lastRelease);
                module.changes.stream().limit(8).forEach(f -> System.out.println("               " + f));
                if (module.changes.size() > 8) {
                    System.out.println("               ... and " + (module.changes.size() - 8) + " more");
                }
                anyChanged = true;
            } else {
                System.out.println("  unchanged  " + module.artifactId + ": nothing since " + module.lastRelease
                    + (module.pomChanged ? ", but its pom.xml changed: check that" : ""));
            }
        }
        System.out.println();
        System.out.println("Only changes to the source and the build files of a bundle count. Its tests and its");
        System.out.println("documentation are ignored, and so is its pom apart from the changes that are not about");
        System.out.println("versions, which are reported with the bundle. Also check the changes of the modules a");
        System.out.println("bundle depends on.");
        System.out.println();

        if (!anyChanged) {
            System.out.println("No bundle changed since its last release, there is nothing to release.");
            System.out.println();
            return;
        }

        List<String> options = new ArrayList<>();
        System.out.println("Proposed versions:");
        int width = Math.max(parent.artifactId.length(), modules.stream().mapToInt(m -> m.artifactId.length()).max().orElse(0)) + 2;
        propose(parent, true, options, width);
        for (Module module : modules) {
            propose(module, !module.bundle || module.changed(), options, width);
        }
        System.out.println();
        System.out.println("Prepare the release with these versions:");
        System.out.println();
        System.out.println("  mvn release:prepare -B \\");
        for (int i = 0; i < options.size(); i++) {
            System.out.println("    " + options.get(i) + (i < options.size() - 1 ? " \\" : ""));
        }
        System.out.println();
        System.out.println("Then perform it. A bundle whose version has been released before, which are the unchanged");
        System.out.println("ones, is neither deployed nor tagged again:");
        System.out.println();
        System.out.println("  mvn release:perform");
        System.out.println();
        System.out.println("Add -DdryRun=true to the prepare to see what it would do, and mention the bundles that are");
        System.out.println("released in changelog.txt before you start.");
        System.out.println();
    }

    /** Prints the proposal of a module and adds the options for the release plugin. */
    private static void propose(Module module, boolean release, List<String> options, int width) {
        String release_;
        String next;
        if (release) {
            release_ = releaseVersion(module.version);
            next = nextVersion(release_);
        } else {
            release_ = module.lastRelease;
            next = module.version;
        }
        System.out.println("  " + pad(module.artifactId, width) + pad(module.version, 18) + "-> " + pad(release_, 8)
            + (release ? "" : " (as released, not deployed again)") + "   next " + next);
        String key = GROUP_ID + ":" + module.artifactId;
        options.add("-Dproject.rel." + key + "=" + release_);
        options.add("-Dproject.dev." + key + "=" + next);
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** x.y.z-SNAPSHOT is released as x.y.z when z is even, and as x.y.(z+1) when it is odd. */
    private static String releaseVersion(String snapshot) {
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)-SNAPSHOT").matcher(snapshot);
        if (!m.matches()) {
            throw new IllegalStateException("Not a snapshot version: " + snapshot);
        }
        int patch = Integer.parseInt(m.group(3));
        return m.group(1) + "." + m.group(2) + "." + (patch % 2 == 0 ? patch : patch + 1);
    }

    /** The development version after a release x.y.z is x.y.(z+1)-SNAPSHOT. */
    private static String nextVersion(String release) {
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(release);
        if (!m.matches()) {
            throw new IllegalStateException("Not a release version: " + release);
        }
        return m.group(1) + "." + m.group(2) + "." + (Integer.parseInt(m.group(3)) + 1) + "-SNAPSHOT";
    }

    /** The version of the latest release of an artifact, from its git tags, or null if it was never released. */
    private static String lastRelease(File base, String artifactId) throws IOException, InterruptedException {
        String prefix = artifactId + "-";
        for (String tag : git(base, "tag", "--list", prefix + "[0-9]*", "--sort=-version:refname")) {
            if (tag.substring(prefix.length()).matches("\\d+\\.\\d+\\.\\d+")) {
                return tag.substring(prefix.length());
            }
        }
        return null;
    }

    private static List<String> changes(File base, Module module) throws IOException, InterruptedException {
        String tag = module.artifactId + "-" + module.lastRelease;
        List<String> files = git(base, "diff", "--relative", "--name-only", tag + "^{commit}", "HEAD", "--", module.dir);
        return files.stream().filter(f -> !ignored(f, module.dir)).collect(Collectors.toList());
    }

    /** If the pom of the module changed in another way than its version and scm tag, which every release changes. */
    private static boolean pomChanged(File base, Module module) throws IOException, InterruptedException {
        String tag = module.artifactId + "-" + module.lastRelease;
        List<String> lines = git(base, "diff", "--relative", "-U0", tag + "^{commit}", "HEAD", "--", module.dir + "/pom.xml");
        return lines.stream()
            .filter(l -> (l.startsWith("+") || l.startsWith("-")) && !l.startsWith("+++") && !l.startsWith("---"))
            .anyMatch(l -> !l.contains("<version>") && !l.contains("<tag>"));
    }

    /** The files that are no reason to release a bundle again. */
    private static boolean ignored(String file, String dir) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        return name.equals("pom.xml")
            || name.equals("changelog.txt")
            || name.startsWith("README")
            || file.startsWith(dir + "/test/")
            || file.endsWith(".releaseBackup");
    }

    private static List<String> git(File dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        // what git says on stderr is not part of the result
        File errors = File.createTempFile("release-plan", ".err");
        try {
            Process process = new ProcessBuilder(command).directory(dir).redirectError(errors).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed: " + output
                    + new String(Files.readAllBytes(errors.toPath()), StandardCharsets.UTF_8)
                    + "\nFetch the tags of the repository first: git fetch origin --tags");
            }
            return Arrays.stream(output.split("\\R")).filter(l -> !l.isEmpty()).collect(Collectors.toList());
        } finally {
            errors.delete();
        }
    }

    private static Document parse(File pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(pom);
    }

    private static String xpath(Document doc, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, doc).trim();
    }

    private static List<String> xpaths(Document doc, String expression) throws Exception {
        org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList) XPathFactory.newInstance().newXPath()
            .evaluate(expression, doc, javax.xml.xpath.XPathConstants.NODESET);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add(nodes.item(i).getTextContent().trim());
        }
        return result;
    }
}
