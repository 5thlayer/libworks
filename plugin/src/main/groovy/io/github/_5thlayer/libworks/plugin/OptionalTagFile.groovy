// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.nio.file.Path

/**
 * A file a Module ships in another Module's data folder, which passes only as a tag of optional entries of the Module's own ids that keeps
 * the tag's other entries (libworks ADR 0014, A Touch Of ADR-0002).
 */
final class OptionalTagFile {

    private OptionalTagFile() {
    }

    /** Why {@code file}, under the other Module's {@code namespace} folder and shown as {@code name}, is not such a tag; empty when it is. */
    static List<String> findings(Path namespace, Path file, String name, String modId, List<String> forbidden) {
        def tags = namespace.resolve('tags')
        if (!file.startsWith(tags)) return ["$name is outside tags/".toString()]
        // A tag file sits in its registry's folder, tags/<registry>/; vanilla reads none directly under tags/.
        if (file.parent == tags || !file.toString().endsWith('.json')) return ["$name is not a tag".toString()]
        def tag
        try {
            tag = new JsonSlurper().parse(file.toFile())
        } catch (Exception ignored) {
            return ["$name is not valid JSON".toString()]
        }
        if (!(tag instanceof Map)) return ["$name is not a tag".toString()]
        def found = []
        // NeoForge reads a tag's "remove" list too, which would take entries out of the other Module's tag.
        if (tag.containsKey('remove')) found << "$name removes from the tag".toString()
        if (!(tag.values instanceof List)) return found ?: ["$name is not a tag".toString()]
        if (tag.replace == true) found << "$name replaces the tag".toString()
        else if (tag.containsKey('replace') && tag.replace != false) found << "$name sets replace to ${JsonOutput.toJson(tag.replace)}, not a boolean".toString()
        tag.values.each { entry ->
            def id = entry instanceof Map ? entry.id : entry
            if (!(id instanceof String)) {
                found << "$name has an entry with no id".toString()
                return
            }
            // A bare id, or an entry with no "required", is required: the tag fails to load without it.
            if (!(entry instanceof Map) || entry.required != false) {
                found << "$name requires $id".toString()
                return
            }
            def tagRef = id.startsWith('#') ? '#' : ''
            def path = id - ~/^#/
            // Vanilla reads an id with no namespace as minecraft's.
            def full = path.contains(':') ? path : "minecraft:$path"
            def namespaceOf = full.split(':')[0]
            // An id in a forbidden namespace is already named by PlaysAlone.ids.
            if (namespaceOf != modId && !forbidden.contains(namespaceOf)) found << "$name adds $tagRef$full, not $modId's own".toString()
        }
        found
    }
}
