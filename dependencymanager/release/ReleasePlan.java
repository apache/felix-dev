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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Releases the Dependency Manager bundles that changed, and guides you through it.
 * <p>
 * Run it with <code>mvn -N exec:exec@release-plan</code> in the dependencymanager directory. It
 * <ol>
 * <li>checks that the checkout is ready for a release,</li>
 * <li>finds the bundles that changed since the git tag of their latest release, and proposes their versions,</li>
 * <li>asks you to confirm the versions, and then runs <code>release:prepare</code> and <code>release:perform</code>
 * with them, each after you confirmed it.</li>
 * </ol>
 * Run it with <code>-Dplan.only=true</code> to only print the proposal and the commands, and to change nothing.
 * <p>
 * A bundle (a module that tags itself when a release is performed, see the parent pom) needs a new release when
 * something other than its tests and its documentation changed since the git tag of its latest release
 * (<code>artifactId-version</code>). The bundles that did not change are put back to the version they already have,
 * which makes the release plugin tag and deploy only the changed ones, see the release profile in the parent pom.
 * <p>
 * Versions follow the Felix convention: a development version has an odd patch number (5.0.1-SNAPSHOT) and is
 * released with the next even one (5.0.2).
 */
public class ReleasePlan {
    private static final String GROUP_ID = "org.apache.felix";

    /** The exit code when the release is stopped on purpose, the pom does not take it for a failure. */
    private static final int STOPPED = 3;

    private static final BufferedReader INPUT = new BufferedReader(new InputStreamReader(System.in));

    /** Thrown when the release is stopped. */
    private static class Stop extends Exception {
        private static final long serialVersionUID = 1L;

        Stop(String message) {
            super(message);
        }
    }

    /** What a module is, as far as the release is concerned. */
    private static class Module {
        final String dir;
        final String artifactId;
        final String version;
        final boolean bundle;
        final boolean bundlePackaging;
        String lastRelease;
        List<String> changes = new ArrayList<>();
        boolean pomChanged;
        /** The version to release, and the development version after that. */
        String release;
        String next;

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

        /** If the release plugin has to deploy this module. */
        boolean released() {
            return !bundle || changed();
        }
    }

    public static void main(String[] args) throws Exception {
        boolean planOnly = Boolean.getBoolean("plan.only");
        File base = new File(".").getCanonicalFile();
        try {
            if (!planOnly) {
                preflight(base);
            }
            Document parentPom = parse(new File(base, "pom.xml"));
            Module parent = new Module(".", xpath(parentPom, "/project/artifactId"), xpath(parentPom, "/project/version"), false, false);
            List<Module> modules = modules(base, parentPom);
            if (!print(parent, modules)) {
                return;
            }
            if (planOnly) {
                printCommands(options(parent, modules));
                return;
            }
            release(base, parent, modules);
        } catch (Stop stop) {
            System.out.println();
            System.out.println("Stopped: " + stop.getMessage());
            System.exit(STOPPED);
        }
    }

    // ------------------------------------------------------------------------------------------------ the checks

    /** Checks that the checkout is ready for a release, and gets the tags: they tell what was released before. */
    private static void preflight(File base) throws Exception {
        System.out.println();
        System.out.println("Dependency Manager release");
        System.out.println("==========================");
        System.out.println();
        if (!git(base, "status", "--porcelain").isEmpty()) {
            throw new Stop("the working tree has changes, commit or stash them first (git status).");
        }
        String branch = git(base, "rev-parse", "--abbrev-ref", "HEAD").get(0);
        if (!branch.equals("master") && !branch.equals("main")) {
            mustConfirm("You are on the branch " + branch + ", and not on master. Release from it anyway?", false);
        }
        System.out.println("Fetching the tags and the latest commits of origin...");
        git(base, "fetch", "origin", "--tags");
        List<String> counts = git(base, "rev-list", "--left-right", "--count", "origin/" + branch + "...HEAD");
        if (counts.isEmpty()) {
            throw new Stop("cannot compare " + branch + " with origin/" + branch + ".");
        }
        String[] behindAhead = counts.get(0).split("\\s+");
        if (!behindAhead[0].equals("0")) {
            throw new Stop(branch + " is " + behindAhead[0] + " commit(s) behind origin/" + branch + ", pull first.");
        }
        if (!behindAhead[1].equals("0")) {
            mustConfirm(branch + " has " + behindAhead[1] + " commit(s) that are not on origin yet. The release pushes them too. Continue?", false);
        }
    }

    /** The modules of the reactor. */
    private static List<Module> modules(File base, Document parentPom) throws Exception {
        List<Module> modules = new ArrayList<>();
        for (String dir : xpaths(parentPom, "/project/modules/module")) {
            Document pom = parse(new File(base, dir + "/pom.xml"));
            String version = xpath(pom, "/project/version");
            if (version.isEmpty()) {
                version = xpath(pom, "/project/parent/version");
            }
            boolean bundle = !xpath(pom, "//plugin[artifactId='maven-antrun-plugin']/artifactId").isEmpty();
            // the modules of the tests and the samples are not released, and neither are they deployed
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
        return modules;
    }

    // ------------------------------------------------------------------------------------------------ the plan

    /** Prints what changed. Returns false when there is nothing to release. */
    private static boolean print(Module parent, List<Module> modules) {
        System.out.println();
        System.out.println("What changed since the latest release of every bundle:");
        System.out.println();
        boolean anyChanged = false;
        for (Module module : modules) {
            if (module.bundlePackaging && !module.bundle) {
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
        }
        return anyChanged;
    }

    /** Proposes the versions, and gives the options for the release plugin. */
    private static List<String> options(Module parent, List<Module> modules) {
        List<String> options = new ArrayList<>();
        propose(parent, true);
        for (Module module : modules) {
            propose(module, module.released());
        }
        add(parent, options);
        modules.forEach(m -> add(m, options));
        return options;
    }

    private static void propose(Module module, boolean release) {
        if (release) {
            module.release = releaseVersion(module.version);
            module.next = nextVersion(module.release);
        } else {
            module.release = module.lastRelease;
            module.next = module.version;
        }
    }

    private static void add(Module module, List<String> options) {
        String key = GROUP_ID + ":" + module.artifactId;
        options.add("-Dproject.rel." + key + "=" + module.release);
        options.add("-Dproject.dev." + key + "=" + module.next);
    }

    private static void printVersions(Module parent, List<Module> modules) {
        List<Module> all = new ArrayList<>();
        all.add(parent);
        all.addAll(modules);
        int width = all.stream().mapToInt(m -> m.artifactId.length()).max().orElse(0) + 2;
        for (Module module : all) {
            boolean release = module == parent || module.released();
            System.out.println("  " + pad(module.artifactId, width) + pad(module.version, 18) + "-> " + pad(module.release, 8)
                + (release ? "" : " (as released, not deployed again)") + "   next " + module.next);
        }
    }

    private static void printCommands(List<String> options) {
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
        System.out.println("Or leave out -Dplan.only=true to be guided through all of it.");
        System.out.println();
    }

    // ------------------------------------------------------------------------------------------------ the release

    private static void release(File base, Module parent, List<Module> modules) throws Exception {
        options(parent, modules);

        System.out.println("Proposed versions:");
        System.out.println();
        printVersions(parent, modules);
        System.out.println();
        System.out.println("The modules that are not bundles (tests, samples) get the same kind of version as the parent.");
        System.out.println();

        // the versions of the parent and of the bundles that change can be changed
        parent.release = askVersion(parent, parent.release);
        parent.next = nextVersion(parent.release);
        for (Module module : modules) {
            if (module.bundle && module.released()) {
                module.release = askVersion(module, module.release);
                module.next = nextVersion(module.release);
            }
        }
        // the other modules follow the parent
        for (Module module : modules) {
            if (!module.bundle) {
                module.release = parent.release;
                module.next = parent.next;
            }
        }

        System.out.println();
        System.out.println("The release:");
        System.out.println();
        printVersions(parent, modules);
        System.out.println();
        checkChangelog(base, modules);

        List<String> options = new ArrayList<>();
        add(parent, options);
        modules.forEach(m -> add(m, options));

        // this builds everything, and changes nothing but the poms in the working tree, until it is done
        if (confirm("Run a dry run of release:prepare first? It builds everything and changes nothing, but the real\n"
            + "prepare builds everything too, before it commits anything. Dry run?", false)) {
            List<String> dryRun = mvn("release:prepare", "-B", "-DdryRun=true", "-Dresume=false");
            dryRun.addAll(options);
            if (run(base, dryRun) != 0) {
                run(base, mvn("release:clean", "-B"));
                throw new Stop("the dry run failed, see the output above. Nothing has been changed.");
            }
            run(base, mvn("release:clean", "-B"));
            System.out.println();
        }

        System.out.println("release:prepare builds and tests everything. It then commits the release versions,");
        System.out.println("tags the whole build as " + parent.artifactId + "-" + parent.release + ", commits the next development versions,");
        System.out.println("and pushes all that to origin.");
        if (!confirm("Prepare the release?", false)) {
            throw new Stop("nothing has been changed.");
        }
        List<String> prepare = mvn("release:prepare", "-B", "-Dresume=false");
        prepare.addAll(options);
        if (run(base, prepare) != 0) {
            System.out.println();
            System.out.println("release:prepare failed, see the output above.");
            if (confirm("Roll back what it did (release:rollback and release:clean)? The commits and tags that were\n"
                + "pushed are not rolled back. Roll back?", true)) {
                run(base, mvn("release:rollback", "-B"));
                run(base, mvn("release:clean", "-B"));
            }
            throw new Stop("the release was not prepared.");
        }

        System.out.println();
        System.out.println("The release is prepared and pushed. release:perform builds the tag, signs the artifacts (you are");
        System.out.println("asked for your gpg passphrase) and deploys them to a staging repository of the Apache Nexus.");
        System.out.println("Only the parent pom and the bundles that changed are deployed.");
        if (!confirm("Perform the release now?", true)) {
            System.out.println();
            System.out.println("Run mvn release:perform when you are ready.");
            return;
        }
        if (run(base, mvn("release:perform", "-B")) != 0) {
            throw new Stop("release:perform failed, see the output above. When it is fixed, run mvn release:perform again:\n"
                + "the bundles that were deployed before it failed are deployed again. Drop the staging repository first.");
        }

        printNextSteps(parent, modules);
    }

    private static String askVersion(Module module, String proposal) throws Stop, IOException {
        while (true) {
            String version = ask("Release version of " + module.artifactId + "?", proposal);
            if (version.matches("\\d+\\.\\d+\\.\\d+")) {
                return version;
            }
            System.out.println("  Not a release version like 5.0.2.");
        }
    }

    /** The changelog lists the bundles that are part of the release: warn if it does not list what is released. */
    private static void checkChangelog(File base, List<Module> modules) throws IOException, Stop {
        File file = new File(base, "changelog.txt");
        String changelog = file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
        List<String> missing = new ArrayList<>();
        for (Module module : modules) {
            if (module.bundle && module.released() && !changelog.contains(module.artifactId + "; version=" + module.release)) {
                missing.add(module.artifactId + "; version=" + module.release);
            }
        }
        if (!missing.isEmpty()) {
            System.out.println("changelog.txt does not list:");
            missing.forEach(m -> System.out.println("    * " + m));
            mustConfirm("Release anyway? Add them, commit that and push it first otherwise.", false);
        }
    }

    private static void printNextSteps(Module parent, List<Module> modules) {
        System.out.println();
        System.out.println("The release is staged. What is left:");
        System.out.println();
        System.out.println("  1. Log in to https://repository.apache.org, check the staging repository (the pom, jar, sources,");
        System.out.println("     javadoc, signatures and checksums of the artifacts below), and close it.");
        modules.stream().filter(m -> m.bundle && m.released())
            .forEach(m -> System.out.println("       " + m.artifactId + " " + m.release));
        System.out.println("     (and " + parent.artifactId + " " + parent.release + ")");
        System.out.println("  2. Verify it: sh check_staged_release.sh <staging repository number> /tmp/felix-staging");
        System.out.println("  3. Call the vote on dev@felix.apache.org, see README.release.");
        System.out.println("  4. When the vote has passed: release the staging repository, copy the release to");
        System.out.println("     dist.apache.org, and update the website and Jira.");
        System.out.println();
    }

    // ------------------------------------------------------------------------------------------------ versions

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

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------------------------ git

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

    // ------------------------------------------------------------------------------------------------ running mvn

    /** The command to run the Maven that runs this: the maven.home property is passed by the pom. */
    private static List<String> mvn(String... goals) {
        String home = System.getProperty("maven.home", "");
        boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");
        List<String> command = new ArrayList<>();
        File mvn = new File(home, "bin/" + (windows ? "mvn.cmd" : "mvn"));
        command.add(!home.isEmpty() && mvn.isFile() ? mvn.getPath() : (windows ? "mvn.cmd" : "mvn"));
        command.addAll(Arrays.asList(goals));
        return command;
    }

    /** Runs a command with the console of this process, and returns its exit code. */
    private static int run(File dir, List<String> command) throws IOException, InterruptedException {
        System.out.println();
        System.out.println("$ " + String.join(" ", command));
        System.out.println();
        return new ProcessBuilder(command).directory(dir).inheritIO().start().waitFor();
    }

    // ------------------------------------------------------------------------------------------------ asking

    /** Asks a question, and returns the answer or the default when you just press enter. */
    private static String ask(String question, String proposal) throws Stop, IOException {
        System.out.print(question + " [" + proposal + "] ");
        System.out.flush();
        String line = INPUT.readLine();
        if (line == null) {
            throw new Stop("there is no input to answer \"" + question + "\". Run it from a terminal.");
        }
        return line.trim().isEmpty() ? proposal : line.trim();
    }

    /** Asks for a yes, a no stops the release. */
    private static void mustConfirm(String question, boolean defaultYes) throws Stop, IOException {
        if (!confirm(question, defaultYes)) {
            throw new Stop("nothing has been changed.");
        }
    }

    /** Asks for a yes or no. */
    private static boolean confirm(String question, boolean defaultYes) throws Stop, IOException {
        while (true) {
            System.out.println();
            String answer = ask(question + " (" + (defaultYes ? "Y/n" : "y/N") + ")", defaultYes ? "y" : "n").toLowerCase();
            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ xml

    private static Document parse(File pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(pom);
    }

    private static String xpath(Document doc, String expression) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(expression, doc).trim();
    }

    private static List<String> xpaths(Document doc, String expression) throws Exception {
        NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath().evaluate(expression, doc, XPathConstants.NODESET);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            result.add(nodes.item(i).getTextContent().trim());
        }
        return result;
    }
}
