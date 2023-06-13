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
package org.spldev.varcs.analyzer.cpp;

import de.featjar.util.logging.Logger;
import de.ovgu.spldev.featurecopp.config.Configuration;
import de.ovgu.spldev.featurecopp.config.Configuration.UserConf;
import de.ovgu.spldev.featurecopp.lang.cpp.CPPAnalyzer;
import de.ovgu.spldev.featurecopp.splmodel.FeatureModule;
import de.ovgu.spldev.featurecopp.splmodel.FeatureTree;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.prop4j.Node;
import org.prop4j.NodeReader;
import org.prop4j.NodeReader.ErrorHandling;

public class PresenceConditionReader {

    private static class NullStream extends PrintStream {
        public NullStream() {
            super(new OutputStream() {
                @Override
                public void write(int b) {}

                @Override
                public void write(byte[] arg0, int arg1, int arg2) throws IOException {}

                @Override
                public void write(byte[] arg0) throws IOException {}
            });
        }
    }

    private static class LevelComparator implements Comparator<FeatureModule.FeatureOccurrence> {
        @Override
        public int compare(FeatureModule.FeatureOccurrence occ1, FeatureModule.FeatureOccurrence occ2) {
            return Integer.compare(getLevel(occ1), getLevel(occ2));
        }

        private int getLevel(FeatureModule.FeatureOccurrence featureOccurrence) {
            final FeatureModule.FeatureOccurrence enclosingFeatureOccurence = featureOccurrence.enclosing;
            return enclosingFeatureOccurence != null ? getLevel(enclosingFeatureOccurence) + 1 : 0;
        }
    }

    private final NodeReader nodeReader = new NodeReader();
    private final CPPAnalyzer cppAnalyzer;

    public PresenceConditionReader() {
        nodeReader.activateJavaSymbols();
        nodeReader.setIgnoreMissingFeatures(ErrorHandling.REMOVE);
        nodeReader.setIgnoreUnparsableSubExpressions(ErrorHandling.REMOVE);
        nodeReader.setFeatureNames(null);

        Configuration.REPORT_ONLY = true;

        final de.ovgu.spldev.featurecopp.log.Logger logger = new de.ovgu.spldev.featurecopp.log.Logger();
        logger.addInfoStream(new NullStream());
        logger.addFailStream(new NullStream());

        final UserConf config = Configuration.getDefault();
        config.setInputDirectory("");
        config.setMacroPattern(".*");

        cppAnalyzer = new CPPAnalyzer(logger, config);
    }

    public List<Node> extractPresenceConditions(Path file) throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return extractPresenceConditions(lines);
    }

    public List<Node> extractPresenceConditions(List<String> lines) {
        final StringBuilder sb = new StringBuilder();
        for (final String line : lines) {
            sb.append(line.replaceAll("[\u000B\u000C\u0085\u2028\u2029\n\r]", ""))
                    .append('\n');
        }
        try {
            cppAnalyzer.process(
                    Paths.get("temp"), new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (final Exception e) {
            Logger.logError("Parsing error: " + e.getMessage());
        }

        final Node[] pcs = new Node[lines.size()];

        final HashMap<String, FeatureModule> featureTable = cppAnalyzer.featureTable.featureTable;
        featureTable.values().stream() //
                .flatMap(module -> module.featureOccurrences.stream()) //
                .sorted(new LevelComparator()) //
                .forEach(fo -> {
                    final String expr = getNestedFeatureTree(fo)
                            .featureExprToString()
                            .replace("defined", "")
                            .replace(" ", "");
                    if (fo.getEndLine() > 0) {
                        final Node node = nodeReader.stringToNode(expr);
                        Arrays.fill(pcs, fo.getBeginLine() - 1, fo.getEndLine(), node);
                    } else {
                        Logger.logError("Invalid range of feature occurence: " + expr);
                    }
                });

        return Arrays.asList(pcs);
    }

    private FeatureTree getNestedFeatureTree(FeatureModule.FeatureOccurrence featureOccurrence) {
        final FeatureModule.FeatureOccurrence enclosingFeatureOccurence = featureOccurrence.enclosing;
        if (enclosingFeatureOccurence != null) {
            final FeatureTree featureTree = featureOccurrence.ftree;
            final FeatureTree previousFeatureTree = getNestedFeatureTree(enclosingFeatureOccurence);
            final FeatureTree nestedFeatureTree = new FeatureTree();
            nestedFeatureTree.setKeyword(featureTree.getKeyword());
            nestedFeatureTree.setRoot(new FeatureTree.LogAnd(
                    previousFeatureTree.getRoot(), featureTree.getRoot(), NodeReader.javaSymbols[3]));
            return nestedFeatureTree;
        } else {
            return featureOccurrence.ftree;
        }
    }
}
