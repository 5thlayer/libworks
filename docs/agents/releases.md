# Releases

A release reaches Consumers through the local maven repository (`~/.m2`) at a version of its own. Consumers read the Library from there by version, and the Pack (5thlayer/factoryworks) pins the jar's sha256. That only works if the jar published at a version is the one jar that version ever names.

A Library that names its Modrinth and CurseForge projects in `gradle.properties` also uploads a released version there, from the maintainer's machine (below); one that names none, like Groundworks, publishes nowhere else. CI builds and tests on every push but never publishes.

## The changelog

Each change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` when it lands, written for a Consumer's author in the glossary's terms, with its issue number. A release ships what Unreleased lists, so the changelog is written as the work is done and never reconstructed from commits.

## Cutting a release

`scripts/release.sh <version>` from a clean main. ADR 0001 sets the semver: below 1.0 only a breaking change bumps the minor version, and an addition or a fix is the next patch. The script refuses a version that is already tagged or in `~/.m2`, and an empty Unreleased. It then:

1. sets `mod_version` and turns `## Unreleased` into `## <version>` under a fresh, empty Unreleased
2. runs the build and the game tests, putting both files back if either fails
3. commits `chore: release <version>`, runs `publishToMavenLocal`, and tags `v<version>` with the jar's sha256
4. uploads the jar to Modrinth and CurseForge with `scripts/upload.py` (below), or says it skipped the upload when `gradle.properties` names neither project. A failed upload leaves the local release and the tag in place; the script names the site that failed, and `scripts/upload.py --site <site> <version>` retries it. `--no-upload` stops before this step and prints the upload command, for the release train, which uploads only once the user says to push. Under `MAVEN_REPO_LOCAL`, a trial run, the upload is only a dry run.

It pushes nothing to git, and ends by printing the push command. To try the script out, set `MAVEN_REPO_LOCAL` to a scratch folder and run it in a throwaway clone: a version published to the real `~/.m2` is permanent.

A release that must reach another Library or the Pack follows the `release-train` skill.

## Uploading to Modrinth and CurseForge

`scripts/upload.py <version>` uploads a version already in `~/.m2` to each site whose project `gradle.properties` names: the jar there, byte for byte, with that version's changelog section as its notes, for Minecraft `minecraft_version` on NeoForge, on client and server, as the release type `upload_release_type` names (`release`, `beta` or `alpha`) or, left empty, as beta below 1.0 and release from it. An unknown type is refused before either site is contacted, and `--dry-run` shows the type it would send.

- `modrinth_project_id` and `curseforge_project_id` are the projects. A new Library leaves them empty until its projects exist on the sites (creating them: `docs/agents/publishing.md`), and never borrows another mod's; `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` override them. With neither set, the script refuses and `scripts/release.sh` skips the upload.
- `modrinth_dependencies` (Modrinth project ids) and `curseforge_dependencies` (CurseForge slugs) are the required dependencies, comma separated.

Each site's token comes from the environment and is never printed. The tokens live in 1Password, and `publish/upload.env` names them there; when a token is missing the upload runs itself again through `op run --env-file=publish/upload.env`, which fills them in for that run only. So `scripts/release.sh <version>` and `scripts/upload.py <version>` need nothing exported; 1Password asks to be unlocked. A token belongs to the account, not a project, so every Library uses Beltworks' items:

- Modrinth: `MODRINTH_TOKEN`, the 1Password item "Beltworks Modrinth": a personal access token with the scopes Create versions, Read versions and Read projects.
- CurseForge: `CURSEFORGE_TOKEN`, the 1Password item "Beltworks CurseForge": an upload API token. The upload API can't list a project's files, so the check for a version CurseForge already has reads the website's own listing (`www.curseforge.com/api/v1/mods/<id>/files`), which needs no key but is undocumented: if it changes, that check fails and the upload stops. It doesn't show a file still under CurseForge's review, so a version is never uploaded again while one waits. CurseForge needs the environments named too, Client and Server, or it refuses the file.

It refuses, before contacting either site, a version missing from `~/.m2` or from the changelog, and a jar lacking the licensing `checkJarLicensing` requires (`LICENSE` and `LICENSES/MIT.txt`, which the build puts in every jar). Each site then goes on its own: a site that already has the version, or whose upload fails, is refused without touching the other, and `--site modrinth` or `--site curseforge` retries just that one. `--dry-run` prints the requests and contacts nothing. `MODRINTH_API_URL`, `CURSEFORGE_UPLOAD_URL` and `CURSEFORGE_API_URL` (the listing's site) point it elsewhere, and its tests (`python3 -m unittest discover scripts/tests`) run it against a stand-in server on localhost.

## A published version is final

A version in `~/.m2` never changes. A fix is the next patch version. `publishToMavenLocal` refuses a version that is already there (`build.gradle`), and nothing is deleted or overwritten by hand to get past it. The same holds on Modrinth and CurseForge: `scripts/upload.py` refuses a version a site already has, and nothing there is deleted or replaced.

## Tags

A release is tagged `v<version>`, annotated with its jar's sha256. `git tag -l 'v*' -n9` lists them.
