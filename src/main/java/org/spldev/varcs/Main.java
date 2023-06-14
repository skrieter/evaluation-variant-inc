/*
 * Copyright (C) 2023 Sebastian Krieter
 *
 * This file is part of evaluation-variant-inc.
 *
 * evaluation-variant-inc is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * evaluation-variant-inc is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with evaluation-variant-inc. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <> for further information.
 */
package org.spldev.varcs;

import de.featjar.util.cli.CLIFunction;
import de.featjar.util.io.IO;
import de.featjar.util.io.csv.CSVWriter;
import de.featjar.util.io.namelist.NameListFormat;
import de.featjar.util.io.namelist.NameListFormat.NameEntry;
import de.featjar.util.logging.Logger;
import de.featjar.util.logging.Logger.LogType;
import de.featjar.util.logging.TabFormatter;
import de.featjar.util.logging.TimeStampFormatter;
import de.featjar.util.tree.Trees;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.prop4j.Node;
import org.prop4j.NodeReader;
import org.prop4j.NodeWriter;
import org.spldev.varcs.git.GithubRepositoryConverter;
import org.spldev.varcs.git.LocalRepositoryConverter;
import org.spldev.varcs.git.RepositoryConverter;
import org.spldev.varcs.io.BinaryFileIO;
import org.spldev.varcs.io.CommitNodeIO;
import org.spldev.varcs.io.ConditionIO;
import org.spldev.varcs.io.TextFileIO;
import org.spldev.varcs.structure.BinaryFileNode;
import org.spldev.varcs.structure.CommitNode;
import org.spldev.varcs.structure.TextFileNode;
import org.spldev.varcs.visitors.StatisticVisitor;
import org.spldev.varcs.visitors.StatisticVisitor.Statistic;

public class Main implements CLIFunction {

    public static final Path sourceDirectory = Paths.get("sources");
    public static final Path sourceSystemsDirectory = sourceDirectory.resolve("systems");

    public static final Path genDirectory = Paths.get("gen");
    public static final List<Path> directories = new ArrayList<>();

    public static final Path logDirectory = addDirectory(genDirectory, "logs");
    public static final Path repositoriesDirectory = addDirectory(genDirectory, "repositories");
    public static final Path statisticsDirectory = addDirectory(genDirectory, "statistics");
    public static final Path auxillaryDirectory = addDirectory(genDirectory, "auxillary");

    public static final String propertiesFileExtension = "repo";
    public static final String treeFileName = "commits.tree";
    public static final String formulaFileName = "commit_dependencies.formula";
    public static final String commitConditionFileName = "commit_conditions.dictionary";
    public static final String allConditionFileName = "all_conditions.dictionary";
    public static final String varfilesTextPCDirectoryName = "text_pc";
    public static final String varfilesTextDirectoryName = "text";
    public static final String varfilesBinaryDirectoryName = "binary";

    private static final int FORK_LIMIT = 4_000;
    private static final boolean FORCE_FETCH = false;
    private static final boolean FORCE_CLONE = false;

    private static final boolean overwritePropertyFiles = false;

    private static final boolean overwriteTreeFiles = false;
    private static final boolean overwriteFormulaFiles = false;
    private static final boolean overwriteVarFiles = false;
    private static final boolean overwriteStatisticFiles = false;
    private static final boolean overwriteVarPCFiles = false;

    private static final boolean skipExistingFiles = true;

    public static TabFormatter tabFormatter = new TabFormatter();

    private List<String> systemNames;
    private Map<String, RepositoryProperties> repoMap = new HashMap<>();

    @Override
    public String getIdentifier() {
        return "merge-variants";
    }

    @Override
    public void run(List<String> args) {
        try {
            final String logArg = "-log=";
            int loglevel = args.stream()
                    .filter(a -> a.startsWith(logArg))
                    .findFirst()
                    .map(a -> a.substring(logArg.length()))
                    .map(Integer::parseInt)
                    .orElse(-1);
            installLogger(loglevel);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try {
            createDirectories();
            String systemList =
                    args.stream().filter(a -> !a.startsWith("-")).findFirst().orElse("");
            if (systemList.isEmpty()) {
                analyze("systems.list");
            } else {
                analyze(systemList);
            }
            Logger.logInfo("Finish");
        } catch (IOException e) {
            Logger.logError(e);
        }
    }

    public void analyze(String systemListFileName) {
        readSystemNames(systemListFileName);

        for (final String system : systemNames) {
            Logger.logInfo("Parsing repository sources for " + system);
            tabFormatter.incTabLevel();
            parseRepositorySource(system);
            tabFormatter.decTabLevel();

            Logger.logInfo("Initialize repository " + system);
            tabFormatter.incTabLevel();
            initializeRepository(system);
            tabFormatter.decTabLevel();

            Logger.logInfo("Analyze repository " + system);
            tabFormatter.incTabLevel();
            analyzeRepository(system);
            tabFormatter.decTabLevel();
        }

        // TODO Extract cpp presence conditions
        // TODO Simplify all presence conditions
        // TODO Create feature model
    }

    private void analyzeRepository(final String system) {
        try {
            final RepositoryProperties repository = repoMap.get(system);
            if (repository != null) {
                final Path systemDirectory = auxillaryDirectory.resolve(repository.getName());
                if (!Files.exists(systemDirectory)) {
                    Files.createDirectories(systemDirectory);
                } else if (skipExistingFiles) {
                    Logger.logInfo("Skipping existing system " + system);
                    return;
                }
                try (Git git = repository.getGit()) {
                    Logger.logInfo(String.format("Analyzing system %s (%s)", system, git.toString()));
                    tabFormatter.incTabLevel();

                    final Extractor extractor = new Extractor(git);
                    createCommitTree(repository, extractor);
                    // printCommits(extractor);
                    buildCommitFormula(repository, extractor);
                    analyzeTree(repository, extractor);
                    analyzeCPP(repository, extractor);

                    tabFormatter.decTabLevel();

                    computeStatistics(system, extractor);
                }
            } else {
                Logger.logInfo("Skipping invalid system " + system);
            }
        } catch (final Exception e) {
            Logger.logError(e);
        }
    }

    private void computeStatistics(String systemName, Extractor extractor) throws IOException {
        final RepositoryProperties repository = repoMap.get(systemName);
        final String fileName = repository.getName() + ".other.csv";
        if (overwriteStatisticFiles || !Files.exists(statisticsDirectory.resolve(fileName))) {
            Logger.logInfo("Computing statistics");
            tabFormatter.incTabLevel();

            final CSVWriter csvWriter = new CSVWriter();
            csvWriter.setOutputDirectory(statisticsDirectory);
            csvWriter.setFileName(fileName);
            csvWriter.setHeader(
                    "Name",
                    "Files",
                    "TextFiles",
                    "BinaryFiles",
                    "VarLines",
                    "RepoSize",
                    "TreeSize",
                    "FormulaSize",
                    "CommitConditionsSize",
                    "AllConditionsSize",
                    "VarTextFilesSize",
                    "VarTextPCFilesSize",
                    "VarBinaryFilesSize",
                    "Literals",
                    "ActiveBinFiles",
                    "ActiveTextFiles",
                    "ActiveLines");

            csvWriter.createNewLine();
            csvWriter.addValue(repository.getName());

            final HashMap<String, TextFileNode> textFileMap =
                    extractor.getFileMap().getTextFileMap();
            final HashMap<String, BinaryFileNode> binaryFileMap =
                    extractor.getFileMap().getBinaryFileMap();
            final HashSet<String> pathSet = new HashSet<>(textFileMap.keySet());
            pathSet.addAll(binaryFileMap.keySet());
            csvWriter.addValue(pathSet.size()); // #files
            csvWriter.addValue(textFileMap.size()); // #text files
            csvWriter.addValue(binaryFileMap.size()); // #bin files

            csvWriter.addValue(textFileMap.values().stream()
                    .mapToInt(f -> f.getDataNodes().size())
                    .sum()); // #var
            // lines in
            csvWriter.addValue(getDirectorySize(repositoriesDirectory.resolve(systemName))); // repo size

            final Path systemDirectory = auxillaryDirectory.resolve(repository.getName());
            csvWriter.addValue(systemDirectory.resolve(treeFileName).toFile().length()); // tree size
            csvWriter.addValue(systemDirectory.resolve(formulaFileName).toFile().length());
            csvWriter.addValue(
                    systemDirectory.resolve(commitConditionFileName).toFile().length());
            csvWriter.addValue(
                    systemDirectory.resolve(allConditionFileName).toFile().length());

            csvWriter.addValue(getDirectorySize(systemDirectory.resolve(varfilesTextDirectoryName)));
            csvWriter.addValue(getDirectorySize(systemDirectory.resolve(varfilesTextPCDirectoryName)));
            csvWriter.addValue(getDirectorySize(systemDirectory.resolve(varfilesBinaryDirectoryName)));

            csvWriter.addValue(extractor.getFormula().getUniqueLiterals().size()); // #literals

            final HashMap<CommitNode, Statistic> statistics = Trees.traverse(
                            extractor.getCommitTree(),
                            new StatisticVisitor(
                                    extractor.getGitUtils(), extractor.getFileMap(), extractor.getFormula()))
                    .orElseThrow();

            csvWriter.addValue(statistics.values().stream()
                    .mapToLong(s -> s.activeBinaryFiles)
                    .average()
                    .getAsDouble());
            csvWriter.addValue(statistics.values().stream()
                    .mapToLong(s -> s.activeTextFiles)
                    .average()
                    .getAsDouble());
            csvWriter.addValue(
                    statistics.values().stream().mapToLong(s -> s.loc).average().getAsDouble());

            csvWriter.flush();
            tabFormatter.decTabLevel();
        }
    }

    private static long getDirectorySize(Path path) throws IOException {
        if (Files.exists(path)) {
            return Files.walk(path)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } else {
            return 0L;
        }
    }

    private static Path addDirectory(Path parent, String name) {
        final Path dir = parent.resolve(name);
        directories.add(dir);
        return dir;
    }

    public static void createDirectories() throws IOException {
        for (final Path path : directories) {
            Files.createDirectories(path);
        }
    }

    public static void installLogger(int logLevel) throws IOException, FileNotFoundException {
        final String curTime =
                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Timestamp(System.currentTimeMillis()));
        final Path currentLogDirectory = logDirectory.resolve(curTime);
        Files.createDirectories(currentLogDirectory);
        switch (logLevel) {
            case 0:
                Logger.setOutLog(LogType.INFO);
                break;
            case 1:
                Logger.setOutLog(LogType.INFO, LogType.PROGRESS);
                break;
            case 2:
            default:
                Logger.setOutLog(LogType.INFO, LogType.PROGRESS, LogType.DEBUG);
                break;
        }
        Logger.setErrLog(LogType.ERROR);
        Logger.addFileLog(currentLogDirectory.resolve("console.log"), LogType.INFO, LogType.DEBUG, LogType.ERROR);
        Logger.addFormatter(new TimeStampFormatter());
        Logger.addFormatter(tabFormatter);
        Logger.install();
    }

    public List<String> getSystemNames() {
        return systemNames;
    }

    private static void createCommitTree(RepositoryProperties repository, Extractor extractor) throws Exception {
        final Path treeFile = auxillaryDirectory.resolve(repository.getName()).resolve(treeFileName);
        if (overwriteTreeFiles || !Files.exists(treeFile)) {
            final CSVWriter csvWriter = new CSVWriter();
            csvWriter.setOutputDirectory(statisticsDirectory);
            csvWriter.setFileName(repository.getName() + ".tree.csv");
            csvWriter.setHeader(
                    "Name",
                    "Forks",
                    "AllBranches",
                    "RemoteBranches",
                    "LocalBranches",
                    "OriginBranches",
                    "Variants",
                    "CommitsAll",
                    "CommitsNoOrphans",
                    "CommitsPruned");

            csvWriter.createNewLine();
            csvWriter.addValue(repository.getName());

            final Git git = extractor.getGitUtils().getGit();
            csvWriter.addValue(git.remoteList().call().size()); // #Forks
            csvWriter.addValue(git.branchList().setListMode(ListMode.ALL).call().size()); // #All Branches
            csvWriter.addValue(
                    git.branchList().setListMode(ListMode.REMOTE).call().size()); // #Remote Branches
            csvWriter.addValue(git.branchList().call().size()); // #Local Branches

            final CommitTree commitTree = new CommitTree(git);
            Logger.logInfo("Identifing variants");
            tabFormatter.incTabLevel();
            commitTree.identifyVariants(true, true, false, null, repository.getMasterBranchName());
            // commitTree.printVariants();
            tabFormatter.decTabLevel();

            csvWriter.addValue(commitTree.getRefMap().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("refs/remotes/origin/"))
                    .count()); // #Origin Branches

            Logger.logInfo("Building commit tree");
            tabFormatter.incTabLevel();
            commitTree.removeDuplicateVariants(false);
            commitTree.buildCommitTree();

            csvWriter.addValue(commitTree.getNumberOfVariants()); // #Variants

            long treeSize = Trees.preOrderStream(commitTree.getRoot()).count();
            final int orphanCount = commitTree.removeOrphans();

            csvWriter.addValue(treeSize + orphanCount); // #Commits (all)
            csvWriter.addValue(treeSize); // #Commits (no orphans)

            Logger.logDebug("Size (complete):   " + (treeSize + orphanCount));
            Logger.logDebug("Size (no orphans): " + (treeSize));
            commitTree.pruneCommitTree();
            commitTree.sortCommitTree();
            treeSize = Trees.preOrderStream(commitTree.getRoot()).count();
            Logger.logDebug("Size (pruned):     " + treeSize);

            csvWriter.addValue(treeSize); // #Commits (pruned)
            csvWriter.flush();
            tabFormatter.decTabLevel();

            Logger.logInfo("Writing commit tree");
            final CommitNode commitTreeRoot = commitTree.getRoot();
            extractor.setCommitTree(commitTreeRoot);
            CommitNodeIO.write(commitTreeRoot, treeFile);
        } else {
            Logger.logInfo("Reading commit tree");
            tabFormatter.incTabLevel();
            final CommitNode commitTreeRoot = CommitNodeIO.read(treeFile);
            extractor.setCommitTree(commitTreeRoot);
            long treeSize = Trees.preOrderStream(commitTreeRoot).count();
            Logger.logDebug("Size: " + treeSize);
            tabFormatter.decTabLevel();
        }
    }

    private static void buildCommitFormula(RepositoryProperties repository, Extractor extractor) throws Exception {
        final Path formulaFile =
                auxillaryDirectory.resolve(repository.getName()).resolve(formulaFileName);
        if (overwriteFormulaFiles || !Files.exists(formulaFile)) {
            Logger.logInfo("Building commit formula");
            tabFormatter.incTabLevel();
            extractor.buildCommitFormula();
            extractor.printFormula();
            tabFormatter.decTabLevel();

            Logger.logInfo("Writing commit formula");
            Files.write(
                    formulaFile,
                    new NodeWriter(extractor.getFormula()).nodeToString().getBytes(StandardCharsets.UTF_8));
        } else {
            Logger.logInfo("Reading formula file");
            tabFormatter.incTabLevel();
            final NodeReader nodeReader = new NodeReader();
            nodeReader.activateShortSymbols();
            final Node formula =
                    nodeReader.stringToNode(new String(Files.readAllBytes(formulaFile), StandardCharsets.UTF_8));
            extractor.setFormula(formula.simplifyTree());
            extractor.printFormula();
            tabFormatter.decTabLevel();
        }
    }

    private static void analyzeTree(RepositoryProperties repository, Extractor extractor) throws Exception {
        final Path systemDirectory = auxillaryDirectory.resolve(repository.getName());
        final Path varTextDirectory = systemDirectory.resolve(varfilesTextDirectoryName);
        final Path varBinaryDirectory = systemDirectory.resolve(varfilesBinaryDirectoryName);
        final Path conditionFile = systemDirectory.resolve(commitConditionFileName);
        if (overwriteVarFiles
                || !Files.exists(conditionFile)
                || !Files.exists(varBinaryDirectory)
                || !Files.exists(varTextDirectory)) {
            Logger.logInfo("Extracting lines");
            tabFormatter.incTabLevel();
            extractor.extractLines();
            tabFormatter.decTabLevel();

            Logger.logInfo("Refreshing node dictionary");
            final FileMap fileMap = extractor.getFileMap();
            fileMap.refreshNodeDictionary();

            Logger.logInfo("Writing var text files");
            tabFormatter.incTabLevel();
            Files.createDirectories(varTextDirectory);
            final HashMap<String, TextFileNode> textFileMap = fileMap.getTextFileMap();
            int numFiles = textFileMap.size();
            if (numFiles > 0) {
                final int digits = numFiles > 1 ? (int) Math.ceil(Math.log10(numFiles)) : 1;
                int fileCounter = 0;
                Files.createDirectories(varTextDirectory);
                for (final TextFileNode fileNode : textFileMap.values()) {
                    final String fileName = String.format("file%0" + digits + "d.text.var", fileCounter++);
                    Logger.logProgress(String.format("Text file %d/%d: %s", fileCounter, numFiles, fileName));
                    TextFileIO.write(fileNode, varTextDirectory.resolve(fileName));
                }
                tabFormatter.decTabLevel();
            }

            Logger.logInfo("Writing var binary files");
            tabFormatter.incTabLevel();
            final HashMap<String, BinaryFileNode> binaryFileMap = fileMap.getBinaryFileMap();
            numFiles = binaryFileMap.size();
            if (numFiles > 0) {
                final int digits = numFiles > 1 ? (int) Math.ceil(Math.log10(numFiles)) : 1;
                int fileCounter = 0;
                Files.createDirectories(varBinaryDirectory);
                for (final BinaryFileNode fileNode : binaryFileMap.values()) {
                    final String fileName = String.format("file%0" + digits + "d.binary.var", fileCounter++);
                    Logger.logProgress(String.format("Binary file %d/%d: %s", fileCounter, numFiles, fileName));
                    BinaryFileIO.write(fileNode, varBinaryDirectory.resolve(fileName));
                }
                tabFormatter.decTabLevel();
            }

            Logger.logInfo("Writing condition file");
            ConditionIO.write(fileMap.getConditionDictionary(), conditionFile);
        } else {
            Logger.logInfo("Reading condition file");
            final FileMap fileMap = new FileMap();
            fileMap.setConditionDictionary(ConditionIO.read(conditionFile));

            Logger.logInfo("Reading var files");
            tabFormatter.incTabLevel();
            final AtomicInteger fileCounter = new AtomicInteger(1);
            Files.walk(varTextDirectory, 1) //
                    .filter(Files::isRegularFile) //
                    .peek(path -> Logger.logProgress(
                            String.format("Text file %d: %s", fileCounter.getAndIncrement(), path))) //
                    .forEach(path -> {
                        try {
                            final TextFileNode fileNode = TextFileIO.read(path);
                            assert (fileMap.getTextFileMap().containsKey(fileNode.getPath()));
                            fileMap.addTextNode(fileNode);
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

            fileCounter.set(1);
            Files.walk(varBinaryDirectory, 1) //
                    .filter(Files::isRegularFile) //
                    .peek(path -> Logger.logProgress(
                            String.format("Binary file %d: %s", fileCounter.getAndIncrement(), path))) //
                    .forEach(path -> {
                        try {
                            final BinaryFileNode fileNode = BinaryFileIO.read(path);
                            assert (fileMap.getBinaryFileMap().containsKey(fileNode.getPath()));
                            fileMap.addBinaryNode(BinaryFileIO.read(path));
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            tabFormatter.decTabLevel();

            extractor.setFileMap(fileMap);
        }
    }

    private static void analyzeCPP(final RepositoryProperties repository, final Extractor extractor)
            throws IOException {

        final Path systemDirectory = auxillaryDirectory.resolve(repository.getName());
        final Path varTextPCDirectory = systemDirectory.resolve(varfilesTextPCDirectoryName);
        final Path conditionFile = systemDirectory.resolve(allConditionFileName);
        if (overwriteVarPCFiles || !Files.exists(conditionFile) || !Files.exists(varTextPCDirectory)) {
            Logger.logInfo("Extracting annotations");
            tabFormatter.incTabLevel();
            extractor.extractAnnotations();
            tabFormatter.decTabLevel();

            Logger.logInfo("Refreshing node dictionary");
            final FileMap fileMap = extractor.getFileMap();
            fileMap.refreshNodeDictionary();

            Logger.logInfo("Writing CPP var files");
            tabFormatter.incTabLevel();
            final HashMap<String, TextFileNode> textFileMap = fileMap.getTextFileMap();
            final int numFiles = textFileMap.size();
            if (numFiles > 0) {
                final int digits = numFiles > 1 ? (int) Math.ceil(Math.log10(numFiles)) : 1;
                int fileCounter = 0;
                Files.createDirectories(varTextPCDirectory);
                for (final TextFileNode fileNode : textFileMap.values()) {
                    final String fileName = String.format("file%0" + digits + "d.text.var", fileCounter++);
                    Logger.logProgress(String.format("PC Text file %d/%d: %s", fileCounter, numFiles, fileName));
                    TextFileIO.write(fileNode, varTextPCDirectory.resolve(fileName));
                }
                tabFormatter.decTabLevel();
            }

            Logger.logInfo("Writing condition file");
            ConditionIO.write(fileMap.getConditionDictionary(), conditionFile);
        } else {
            Logger.logInfo("Reading condition file");
            final FileMap fileMap = extractor.getFileMap();
            fileMap.setConditionDictionary(ConditionIO.read(conditionFile));

            Logger.logInfo("Reading PC var files");
            tabFormatter.incTabLevel();
            fileMap.getTextFileMap().clear();
            final AtomicInteger fileCounter = new AtomicInteger(1);
            Files.walk(varTextPCDirectory, 1) //
                    .filter(Files::isRegularFile) //
                    .peek(path -> Logger.logProgress(
                            String.format("PC Text file %d: %s", fileCounter.getAndIncrement(), path))) //
                    .forEach(path -> {
                        try {
                            final TextFileNode fileNode = TextFileIO.read(path);
                            assert (fileMap.getTextFileMap().containsKey(fileNode.getPath()));
                            fileMap.addTextNode(fileNode);
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            tabFormatter.decTabLevel();
        }
    }

    public void readSystemNames(String fileName) {
        readSystemNames(sourceDirectory.resolve(fileName));
    }

    public void readSystemNames(Path filePath) {
        Logger.logInfo("Reading system names...");
        tabFormatter.incTabLevel();
        final List<NameEntry> nameList =
                IO.load(filePath, new NameListFormat()).orElse(Collections.emptyList(), Logger::logProblems);
        systemNames =
                nameList.stream().map(NameEntry::getName).peek(Logger::logInfo).collect(Collectors.toList());
        tabFormatter.decTabLevel();
    }

    private void initializeRepository(final String system) {
        final Path propertiesFile = repositoriesDirectory.resolve(system + "." + propertiesFileExtension);
        if (Files.isReadable(propertiesFile)) {
            final RepositoryProperties repository = readFromFile(propertiesFile);
            if (repository != null) {
                Logger.logInfo("Reading repo file for: " + system);
                tabFormatter.incTabLevel();
                if (repository.initRepo(FORK_LIMIT, FORCE_CLONE, FORCE_FETCH)) {
                    repoMap.put(system, repository);
                }
                tabFormatter.decTabLevel();
            }
        }
    }

    private void parseRepositorySource(final String system) {
        final Path propertiesFile = repositoriesDirectory.resolve(system + "." + propertiesFileExtension);
        if (overwritePropertyFiles || !Files.exists(propertiesFile)) {
            final Optional<RepositoryConverter> converter = getRepositoryConverter(system, "github", "local");
            if (converter.isPresent()) {
                tabFormatter.incTabLevel();
                final RepositoryProperties repository = converter.get().getRepository();
                if (repository != null) {
                    Logger.logInfo("Writing repo file: " + propertiesFile);
                    writeToFile(repository, propertiesFile);
                } else {
                    Logger.logInfo("Empty repository property for: " + system);
                }
                tabFormatter.decTabLevel();
            } else {
                Logger.logInfo("No source file for " + system);
            }
        }
    }

    private static Optional<RepositoryConverter> getRepositoryConverter(String system, String... extensions) {
        for (final String extension : extensions) {
            final String fileName = system + "." + extension;
            final Path file = sourceSystemsDirectory.resolve(fileName);
            if (Files.isReadable(file)) {
                Logger.logDebug(fileName);
                switch (extension) {
                    case "github":
                        return Optional.of(new GithubRepositoryConverter(file));
                    case "local":
                        return Optional.of(new LocalRepositoryConverter(file));
                    default:
                        break;
                }
            }
        }
        return Optional.empty();
    }

    private static <T> void writeToFile(T object, Path file) {
        try (ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(file.toFile()))) {
            o.writeObject(object);
        } catch (final Exception e) {
            Logger.logError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readFromFile(Path file) {
        try (ObjectInputStream o = new ObjectInputStream(new FileInputStream(file.toFile()))) {
            return (T) o.readObject();
        } catch (final Exception e) {
            Logger.logError(e);
            return null;
        }
    }
}
