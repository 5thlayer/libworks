// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

/**
 * What a Module ships that stops it playing alone (FactoryWorks ADR-0127, A Touch Of ADR-0002), one scan
 * per rule, over its suite's Module list (libworks ADR 0005, ADR 0013). Each finding reads as the Modules'
 * own copies did.
 */
final class PlaysAlone {

    /** One suite: what it calls its Modules, the ADR that has them play alone, its Modules, the base among them that every one may require, and the retired ids none may name. */
    static final class Suite {
        final String term
        final String rule
        final String base
        final List<String> modules
        final List<String> retired

        Suite(String term, String rule, String base, List<String> modules, List<String> retired) {
            this.term = term
            this.rule = rule
            this.base = base
            this.modules = modules
            this.retired = retired
        }

        /** Every Module but {@code modId} and the base, and the retired ids. */
        List<String> forbiddenTo(String modId) {
            modules.findAll { it != modId && it != base } + retired
        }

        /** Every Module, the base included, and the retired ids: what a Module of another suite may not name. */
        List<String> forbiddenToOtherSuites() {
            modules + retired
        }
    }

    /**
     * Every suite. FactoryWorks Core retired (FactoryWorks ADR-0128), so no Module may name {@code factoryworks};
     * the A Touch Of Runtime, {@code atouchof}, is the base every Pillar nests (A Touch Of ADR-0002).
     */
    static final List<Suite> SUITES = [
            new Suite('Module', 'FactoryWorks ADR-0127', 'groundworks',
                    ['beltworks', 'craftworks', 'fieldworks', 'gearworks', 'groundworks', 'pipeworks', 'voidworks', 'wireworks'], ['factoryworks']),
            new Suite('Pillar', 'A Touch Of ADR-0002', 'atouchof',
                    ['atouchof', 'atouchofcold', 'atouchofdarkness', 'atouchofmadness'], []),
    ]

    private static final Pattern DEPENDENCY_MOD_ID = ~/(?m)^\s*modId\s*=\s*"([^"]+)"/
    private static final Pattern DEPENDENCY_TYPE = ~/(?m)^\s*type\s*=\s*"([^"]+)"/

    /** The suite whose Modules hold {@code modId}, or null for a mod no suite lists, such as the template's own. */
    final Suite suite

    /**
     * The namespaces {@code modId} may not name: its suite's but its base, and every other suite's, bases
     * included; or for a mod no suite lists every suite's but their bases, as its suite is not known (ADR 0013).
     */
    final List<String> forbidden

    PlaysAlone(String modId) {
        suite = SUITES.find { modId in it.modules }
        forbidden = (suite
                ? suite.forbiddenTo(modId) + (SUITES - suite).collectMany { it.forbiddenToOtherSuites() }
                : SUITES.collectMany { it.forbiddenTo(modId) }).sort()
    }

    /** What a finding names: another Pillar, or another Module, and the ADRs that forbid it. */
    String naming() {
        suite ? "another ${suite.term} or another suite's Module (${suite.rule}, libworks ADR 0013)" : 'another Module (libworks ADR 0013)'
    }

    /** What the processed resources under {@code resources} ship against the rules: ids, namespace folders and required mods. */
    List<String> resources(Path resources) {
        ids(resources) + namespaces(resources) + required(resources)
    }

    /** What the Java source under {@code code} names against the rules: ids and namespace strings. */
    List<String> code(Path code) {
        ids(code) + namespaceStrings(code)
    }

    /** Each id in a forbidden namespace that a file under {@code root} names, a structure's saved block ids included. */
    List<String> ids(Path root) {
        // No word boundary before the namespace: in a structure's NBT a string's length byte can be a letter.
        def ids = Pattern.compile("(${forbidden.join('|')}):[a-z0-9_./-]+")
        def found = []
        files(root).each { file ->
            def id = ids.matcher(text(file))
            while (id.find()) found << "${relative(root, file)} names ${id.group()}".toString()
        }
        found
    }

    /** Each {@code assets/<namespace>} or {@code data/<namespace>} folder under {@code resources} that is forbidden. */
    List<String> namespaces(Path resources) {
        def found = []
        ['assets', 'data'].each { kind ->
            def dir = resources.resolve(kind)
            if (!Files.isDirectory(dir)) return
            Files.list(dir).withCloseable { namespaces ->
                namespaces.map { it.fileName.toString() }.filter { forbidden.contains(it) }.sorted()
                        .forEach { found << "$kind/$it".toString() }
            }
        }
        found
    }

    /** Each string in the Java source under {@code code} that is a forbidden namespace, as an id built in code names it. */
    List<String> namespaceStrings(Path code) {
        def namespaces = Pattern.compile("\"(${forbidden.join('|')})\"")
        def found = []
        files(code).findAll { it.toString().endsWith('.java') }.each { file ->
            def namespace = namespaces.matcher(Files.readString(file))
            while (namespace.find()) found << "${relative(code, file)} names ${namespace.group()}".toString()
        }
        found
    }

    /** Each forbidden mod the mods.toml under {@code resources} requires; an optional dependency is allowed. */
    List<String> required(Path resources) {
        def toml = resources.resolve('META-INF/neoforge.mods.toml')
        if (!Files.isRegularFile(toml)) return []
        def found = []
        Files.readString(toml).split(/(?m)^(?=\[)/).each { block ->
            def modId = DEPENDENCY_MOD_ID.matcher(block)
            def type = DEPENDENCY_TYPE.matcher(block)
            // NeoForge reads a dependency with no type as required.
            boolean required = !type.find() || type.group(1).equalsIgnoreCase('required')
            if (block.startsWith('[[dependencies.') && modId.find() && forbidden.contains(modId.group(1)) && required) {
                found << "${relative(resources, toml)} requires ${modId.group(1)}".toString()
            }
        }
        found
    }

    private static List<Path> files(Path root) {
        if (!Files.isDirectory(root)) return []
        Files.walk(root).withCloseable { it.filter { Files.isRegularFile(it) }.sorted().toList() }
    }

    static String relative(Path root, Path file) {
        root.relativize(file).toString().replace('\\', '/')
    }

    private static String text(Path file) {
        if (file.toString().endsWith('.nbt')) {
            return new GZIPInputStream(Files.newInputStream(file)).withCloseable { new String(it.readAllBytes(), StandardCharsets.ISO_8859_1) }
        }
        // Latin-1 maps every byte to a char, so a binary file such as a texture still reads.
        new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1)
    }
}
