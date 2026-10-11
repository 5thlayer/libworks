# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Drives .github/scripts/build-upstreams.sh, which module-ci.yml feeds the Module's
# `libworksUpstreams` lines, against local git repositories standing in for GitHub.
# Run with: python3 -m unittest discover scripts/tests
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / ".github/scripts/build-upstreams.sh"


def git(cwd, *args):
    subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=cwd, check=True,
                   capture_output=True)


class BuildUpstreams(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.base = self.tmp / "github"
        self.work = self.tmp / "work"
        self.work.mkdir()

    def upstream(self, repo, tags, plugin=None, upstreams=""):
        """A repository whose gradlew records the task it ran and the version it was tagged at. With
        a plugin version it applies the libworks plugin, and prints upstreams for libworksUpstreams."""
        path = self.base / repo
        path.mkdir(parents=True)
        git(path, "init", "-q")
        for tag in tags:
            (path / "gradlew").write_text(
                '#!/bin/sh\n'
                f'if [ "$*" = "-q libworksUpstreams" ]; then printf "{upstreams}"; exit; fi\n'
                f'echo "{repo} {tag} $*" >> "$PUBLISHED"\n')
            (path / "gradlew").chmod(0o755)
            if plugin:
                (path / "build.gradle").write_text(
                    f"plugins {{\n    id 'io.github.5thlayer.libworks.module' version '{plugin}'\n}}\n")
            git(path, "add", ".")
            git(path, "commit", "-qm", tag)
            git(path, "tag", tag)

    def run_script(self, lines):
        env = dict(os.environ, LIBWORKS_GIT_BASE=str(self.base), PUBLISHED=str(self.tmp / "published"),
                   MAVEN_REPO_LOCAL=str(self.tmp / "m2"))
        return subprocess.run(["sh", str(SCRIPT)], cwd=self.work, input=lines, text=True, env=env,
                              capture_output=True)

    def published(self):
        p = self.tmp / "published"
        return p.read_text() if p.exists() else ""

    def test_publishes_each_upstream_from_its_tag(self):
        self.upstream("5thlayer/groundworks", ["groundworks-v0.5.4", "groundworks-v0.5.5"])
        self.upstream("5thlayer/craft", ["v0.2.0"])
        result = self.run_script("5thlayer/groundworks groundworks-v0.5.4\n5thlayer/craft v0.2.0\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("5thlayer/groundworks groundworks-v0.5.4 publishToMavenLocal\n"
                         "5thlayer/craft v0.2.0 publishToMavenLocal\n", self.published())

    def test_an_upstream_on_the_plugin_has_its_own_upstreams_built_first_once_each(self):
        self.upstream("5thlayer/libworks", ["plugin-v0.1.0"])
        self.upstream("5thlayer/groundworks", ["groundworks-v0.5.4"], plugin="0.1.0")
        self.upstream("5thlayer/beltworks", ["beltworks-v0.3.0"], plugin="0.1.0",
                      upstreams="5thlayer/groundworks groundworks-v0.5.4\\n")
        result = self.run_script("5thlayer/beltworks beltworks-v0.3.0\n5thlayer/groundworks groundworks-v0.5.4\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("5thlayer/libworks plugin-v0.1.0 -p plugin publishToMavenLocal\n"
                         "5thlayer/groundworks groundworks-v0.5.4 publishToMavenLocal\n"
                         "5thlayer/beltworks beltworks-v0.3.0 publishToMavenLocal\n", self.published())

    def test_the_plugin_an_upstream_applies_is_not_rebuilt_when_in_maven_local(self):
        (self.tmp / "m2/io/github/5thlayer/libworks-plugin/0.1.0").mkdir(parents=True)
        self.upstream("5thlayer/groundworks", ["groundworks-v0.5.4"], plugin="0.1.0")
        result = self.run_script("5thlayer/groundworks groundworks-v0.5.4\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("5thlayer/groundworks groundworks-v0.5.4 publishToMavenLocal\n", self.published())

    def test_a_line_naming_a_project_publishes_only_that_project_once_without_recursing(self):
        self.upstream("5thlayer/libworks", ["runtime-v0.1.0"], plugin="0.1.0",
                      upstreams="5thlayer/craft v0.2.0\\n")
        (self.tmp / "m2/io/github/5thlayer/libworks-plugin/0.1.0").mkdir(parents=True)
        self.upstream("5thlayer/groundworks", ["groundworks-v0.5.4"], plugin="0.1.0",
                      upstreams="5thlayer/libworks runtime-v0.1.0 :runtime\\n")
        result = self.run_script("5thlayer/groundworks groundworks-v0.5.4\n5thlayer/libworks runtime-v0.1.0 :runtime\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("5thlayer/libworks runtime-v0.1.0 :runtime:publishToMavenLocal\n"
                         "5thlayer/groundworks groundworks-v0.5.4 publishToMavenLocal\n", self.published())

    def test_no_upstreams_builds_nothing(self):
        result = self.run_script("")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("", self.published())

    def test_a_missing_tag_fails(self):
        self.upstream("5thlayer/craft", ["v0.2.0"])
        result = self.run_script("5thlayer/craft v0.3.0\n")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", self.published())


if __name__ == "__main__":
    unittest.main()
