package io.github._5thlayer.libworks.plugin

import org.gradle.api.GradleException

/**
 * A Library the Module requires at a version range: what it depends on, what a -PsiblingBuilds
 * checkout is checked against, and what CI builds from its tag (ADR 0003).
 */
class Upstream {
    /** The artifact, which is also the checkout's directory name and the repo's under 5thlayer. */
    final String name
    /** A Maven range, such as {@code [0.5.4,0.6)}, with an inclusive lower bound. */
    String range
    String tagPrefix

    Upstream(String name) {
        this.name = name
        this.tagPrefix = "$name-v"
    }

    private List<String> parts() {
        def m = range =~ /^\[([^,\]]+),([^\])]*)([\])])$/
        if (!m.matches()) throw new GradleException(
                "libworks: upstream '$name' range '$range' is not a range with an inclusive lower bound, such as [0.5.4,0.6)")
        [m.group(1), m.group(2), m.group(3)]
    }

    /** The GitHub repo CI checks the tag out of. */
    String getRepo() { "5thlayer/$name" }

    /** The lowest version in range: the one preferred, and the one CI builds. */
    String getLowerBound() { parts()[0] }

    boolean contains(String version) {
        def (lower, upper, close) = parts()
        if (compare(version, lower) < 0) return false
        if (!upper) return true
        close == ']' ? compare(version, upper) <= 0 : compare(version, upper) < 0
    }

    private static int compare(String a, String b) {
        def (x, y) = [a, b].collect { it.tokenize('.')*.toInteger() }
        def i = (0..<Math.max(x.size(), y.size())).find { (x[it] ?: 0) != (y[it] ?: 0) }
        i == null ? 0 : (x[i] ?: 0) <=> (y[i] ?: 0)
    }
}
