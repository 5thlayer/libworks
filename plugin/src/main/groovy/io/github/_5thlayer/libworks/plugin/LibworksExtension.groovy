// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.plugin

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project

/** The {@code libworks { }} block: what a Module requires of the other Modules. */
class LibworksExtension {
    /** The first runtime published with libworks-gametest beside it. */
    private static final String FIRST_GAMETEST = '0.1.8'

    private final Project project
    final List<Upstream> upstreams = []
    /** The Module's own release tags, {@code <artifact>-v<version>} unless set. */
    String tagPrefix
    private String runtime

    LibworksExtension(Project project) {
        this.project = project
        this.tagPrefix = "${project.property('archives_name')}-v"
    }

    void upstream(String name, Action<Upstream> configure) {
        def upstream = new Upstream(name)
        configure.execute(upstream)
        if (!upstream.range) throw new GradleException("libworks: upstream '$name' has no range")
        upstreams << upstream
        require(upstream.optional ? 'localRuntime' : 'implementation', name, upstream.range, upstream.lowerBound)
    }

    String getRuntime() { runtime }

    /** The range libworks-runtime is nested in, or null when runtime is not set. */
    String getRuntimeRange() { runtime ? "[$runtime,${Versions.nextBreaking(runtime)})".toString() : null }

    /**
     * Nests libworks-runtime from this version up to the next breaking one (ADR 0001, docs/runtime.md).
     * From 0.1.8, the Module's game tests also compile against libworks-gametest in the same range, on
     * {@code libworksGametest}, and its dev runs load it; the jar never depends on it (libworks ADR 0008).
     */
    void setRuntime(String version) {
        if (runtime) throw new GradleException("libworks: runtime is set once, and is already $runtime")
        runtime = version
        ['implementation', 'jarJar'].each { require(it, 'libworks-runtime', runtimeRange, version) }
        if (Versions.compare(version, FIRST_GAMETEST) >= 0) require('libworksGametest', 'libworks-gametest', runtimeRange, version)
    }

    /** io.github.5thlayer:<artifact>, strictly in range and preferring its lower bound. Not private: Gradle decorates the extension. */
    protected void require(String configuration, String artifact, String range, String lower) {
        project.dependencies.add(configuration, "io.github.5thlayer:$artifact") {
            it.version {
                it.strictly range
                it.prefer lower
            }
        }
    }
}
