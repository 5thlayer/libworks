// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin

import org.gradle.api.GradleException

/**
 * Another Module this Module requires at a version range: what it depends on, what a -PsiblingBuilds
 * checkout is checked against, and what CI builds from its tag (ADR 0003).
 */
class Upstream {
    /** The artifact, which is also the checkout's directory name and the repo's under 5thlayer. */
    final String name
    String tagPrefix
    /** On localRuntime, for dev runs only: the published Module does not depend on it. */
    boolean optional
    private Versions.Range parsed

    Upstream(String name) {
        this.name = name
        this.tagPrefix = "$name-v"
    }

    /** A Maven range, such as {@code [0.5.4,0.6)}, with an inclusive lower bound: a released version. */
    String getRange() { parsed?.text }

    void setRange(String range) {
        parsed = Versions.Range.parse(range)
        if (!parsed) throw new GradleException(
                "libworks: upstream '$name' range '$range' is not a range with an inclusive lower bound, such as [0.5.4,0.6)")
    }

    /** The GitHub repo CI checks the tag out of. */
    String getRepo() { "5thlayer/$name" }

    /** The lowest version in range: the one preferred, and the one CI builds. */
    String getLowerBound() { parsed.lowerBound }

    boolean contains(String version) { parsed.contains(version) }
}
