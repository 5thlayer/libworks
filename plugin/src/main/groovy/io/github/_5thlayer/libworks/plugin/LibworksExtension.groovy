package io.github._5thlayer.libworks.plugin

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project

/** The {@code libworks { }} block: what a Module requires of the other Libraries. */
class LibworksExtension {
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
        def range = upstream.range
        def lower = upstream.lowerBound
        project.dependencies.add('implementation', "${upstream.group}:${upstream.name}") {
            version {
                strictly range
                prefer lower
            }
        }
    }

    String getRuntime() { runtime }

    /** Nests libworks-runtime from this version up to the next major (docs/runtime.md). */
    void setRuntime(String version) {
        if (runtime) throw new GradleException("libworks: runtime is set once, and is already $runtime")
        runtime = version
        def range = "[$version,1.0)".toString()
        def dependencies = project.dependencies
        ['implementation', 'jarJar'].each { configuration ->
            dependencies.add(configuration, 'io.github.5thlayer:libworks-runtime') {
                it.version {
                    it.strictly range
                    it.prefer version
                }
            }
        }
    }
}
