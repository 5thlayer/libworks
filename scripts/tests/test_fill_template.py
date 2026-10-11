# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Runs scripts/fill-template.sh on a copy of this checkout's working tree, as a repo just made from
# the template, and reads back the Module it leaves.
# Run with: python3 -m unittest discover scripts/tests
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def git(cwd, *args):
    return subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=cwd, check=True,
                          capture_output=True, text=True).stdout


def plugin_version():
    return re.search(r"^plugin_version\s*=\s*(\S+)", (ROOT / "plugin/gradle.properties").read_text(), re.M)[1]


class FillTemplate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = Path(tempfile.mkdtemp())
        cls.repo = cls.tmp / "craftworks"
        files = git(ROOT, "ls-files", "-z", "--cached", "--others", "--exclude-standard").split("\0")
        for name in filter(None, files):
            src = ROOT / name
            # Submodules and deleted files are not the template's own text.
            if not src.is_file() or name.startswith(".claude/worktrees/"):
                continue
            dst = cls.repo / name
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dst)
        git(cls.repo, "init", "-q")
        git(cls.repo, "add", "-A")
        git(cls.repo, "commit", "-qm", "template")
        subprocess.run(["bash", "scripts/fill-template.sh", "craftworks", "Craftworks", "Craftworks"],
                       cwd=cls.repo, check=True, capture_output=True)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.tmp)

    def text(self, name):
        return (self.repo / name).read_text()

    def test_the_module_applies_the_plugin_at_its_released_version(self):
        self.assertIn(f"id 'io.github.5thlayer.libworks.module' version '{plugin_version()}'", self.text("build.gradle"))

    def test_the_module_resolves_the_plugin_from_maven_local_not_an_included_build(self):
        settings = self.text("settings.gradle")
        self.assertIn("mavenLocal()", settings)
        self.assertNotIn("includeBuild", settings)
        self.assertNotIn("include '", settings)

    def test_maven_local_joins_the_one_plugin_repositories_block_after_neoforge(self):
        settings = self.text("settings.gradle")
        self.assertEqual(1, settings.count("repositories {"), settings)
        lines = [line.strip() for line in settings.splitlines()]
        neoforge = next(i for i, line in enumerate(lines) if "maven.neoforged.net" in line)
        self.assertEqual("mavenLocal()", lines[neoforge + 1], settings)

    def test_the_plugin_and_the_runtime_are_gone(self):
        self.assertFalse((self.repo / "plugin").exists())
        self.assertFalse((self.repo / "runtime").exists())

    def test_the_module_keeps_no_scripts_of_its_own(self):
        self.assertFalse((self.repo / "scripts").exists())
        self.assertFalse((self.repo / ".github/scripts").exists())
        self.assertFalse((self.repo / ".github/workflows/module-ci.yml").exists())

    def test_ci_calls_module_ci_at_the_plugin_tag(self):
        ci = self.text(".github/workflows/ci.yml")
        v = plugin_version()
        self.assertIn(f"uses: 5thlayer/libworks/.github/workflows/module-ci.yml@plugin-v{v}", ci)
        self.assertIn(f"plugin-version: {v}", ci)
        self.assertNotIn("scripts/tests", ci)

    def test_the_tree_is_left_staged_with_nothing_untracked(self):
        self.assertEqual("", git(self.repo, "ls-files", "--others", "--exclude-standard"))


if __name__ == "__main__":
    unittest.main()
