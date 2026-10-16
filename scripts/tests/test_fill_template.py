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
        self.assertNotIn("plugin-version", ci)
        self.assertNotIn("scripts/tests", ci)

    def test_the_plugin_and_runtime_docs_are_gone(self):
        self.assertFalse((self.repo / "docs/plugin.md").exists())
        self.assertFalse((self.repo / "docs/runtime.md").exists())

    def test_the_module_keeps_only_adr_0001_so_it_numbers_its_own_from_0002(self):
        adrs = sorted(p.name for p in (self.repo / "docs/adr").iterdir())
        self.assertEqual(1, len(adrs), adrs)
        self.assertTrue(adrs[0].startswith("0001-"), adrs)

    def test_no_file_names_a_libworks_adr_as_the_modules_own(self):
        # Every ADR the template holds but 0001 is libworks' own, by number or by path.
        numbers = [p.name[:4] for p in (ROOT / "docs/adr").glob("*.md") if not p.name.startswith("0001-")]
        pattern = re.compile(rf"(ADR[ -]|adr/)({'|'.join(numbers)})")
        for name in filter(None, git(self.repo, "ls-files", "-z").split("\0")):
            path = self.repo / name
            if path.suffix in (".png", ".nbt", ".jar") or not path.is_file():
                continue
            text = path.read_text(errors="ignore")
            for m in pattern.finditer(text):
                # Qualified as libworks' own, or as another project's ("Craftworks' ADR 0008"), or
                # docs/agents/domain.md's made-up example of an ADR conflict.
                qualified = re.search(r"(libworks |\w+'s? |^> _Contradicts )\Z", text[:m.start()], re.M)
                self.assertTrue(qualified, f"{name}: {text[max(0, m.start() - 40):m.end()]!r}")

    def test_reuse_names_no_path_the_fill_removes(self):
        reuse = self.text("REUSE.toml")
        self.assertNotIn("plugin/", reuse)
        self.assertNotIn('"scripts/', reuse)

    def test_the_module_keeps_notice_with_no_entries_and_reuse_covers_it(self):
        notice = self.text("NOTICE")
        self.assertTrue(notice.startswith("Third-party material committed to this repository\n"), notice)
        self.assertNotIn("Files:", notice)
        self.assertRegex(self.text("REUSE.toml"), r'(?m)^    "NOTICE",$')

    def test_the_changelog_keeps_its_header_and_an_empty_unreleased(self):
        changelog = self.text("CHANGELOG.md")
        self.assertTrue(changelog.startswith("# Changelog\n"), changelog)
        self.assertIn("Craftworks", changelog)
        self.assertTrue(changelog.rstrip().endswith("## Unreleased"), changelog)

    def test_the_readme_is_a_stub_for_the_module(self):
        readme = self.text("README.md")
        self.assertTrue(readme.startswith("# Craftworks\n"), readme)
        self.assertNotIn("template", readme.lower())
        self.assertNotIn("Starting a Module", readme)

    def test_the_readme_opens_with_a_placeholder_for_the_maintainer_s_section_for_players(self):
        readme = self.text("README.md")
        title, _, rest = readme.partition("\n\n")
        self.assertEqual("# Craftworks", title)
        self.assertTrue(rest.startswith("<!-- For players"), readme)
        self.assertIn("docs/agents/publishing.md", rest.split("-->")[0])

    def test_build_gradle_is_the_template_s_less_its_own_build_of_the_plugin(self):
        build = self.text("build.gradle")
        self.assertNotIn("plugin/", build)
        self.assertNotIn("includedBuild", build)
        self.assertIn("libworks ADR 0003", build)
        # What the plugin provides and the libworks { } example stay as the template words them.
        self.assertIn("buildGametestStructures tasks", build)
        self.assertIn("upstream('groundworks')", build)
        # The Works tab the template starts with (#26): the runtime it nests, from the ~/.m2 the plugin declares (#18).
        self.assertIn("runtime = '0.1.8'", build)
        self.assertNotIn("mavenLocal", build)

    def test_the_works_tab_and_its_game_test_move_to_the_module_s_id(self):
        package = "io/github/_5thlayer/craftworks"
        self.assertIn("WorksTab.KEY", self.text(f"src/main/java/{package}/WorksTabContents.java"))
        self.assertIn("Craftworks.MOD_ID", self.text(f"src/gametest/java/{package}/gametest/CreativeTabTests.java"))
        self.assertIn('modId = "libworks"', self.text("src/main/resources/META-INF/neoforge.mods.toml"))

    def test_the_gametest_code_and_pack_move_to_the_module_s_id(self):
        dev_packs = self.repo / "src/gametest/java/io/github/_5thlayer/craftworks/gametest/DevPacks.java"
        self.assertIn("Craftworks.MOD_ID", dev_packs.read_text())
        self.assertTrue((self.repo / "src/gametest/resources/gametest_pack/data/craftworks/tags/item/gametest_pack.json").is_file())
        # Game-test code never ships (libworks ADR 0011): the registrar and the platform move with the pack.
        self.assertTrue((self.repo / "src/gametest/java/io/github/_5thlayer/craftworks/gametest/CraftworksGameTests.java").is_file())
        self.assertTrue((self.repo / "src/gametest/resources/data/craftworks/structure/gametest/platform.nbt").is_file())
        self.assertFalse((self.repo / "src/main/java/io/github/_5thlayer/craftworks/gametest").exists())
        self.assertFalse(any("examplelib" in str(p) for p in (self.repo / "src").rglob("*")))

    def test_the_tree_is_left_staged_with_nothing_untracked(self):
        self.assertEqual("", git(self.repo, "ls-files", "--others", "--exclude-standard"))


if __name__ == "__main__":
    unittest.main()
