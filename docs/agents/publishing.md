# Creating the Modrinth and CurseForge projects

A Library goes to Modrinth and CurseForge only once its projects exist there. Creating them is done by hand, once, on each site's website; after that, `scripts/release.sh` uploads every release (`docs/agents/releases.md`). A Library that never goes to the sites skips this page.

The sources were read on 2026-09-28. The sites change their forms, so check the linked page when a field here no longer matches.

## What the Library already has

Most answers are in the repo. Keep them the same on both sites and in the repo: Modrinth's rules ask that metadata be "consistent with information found elsewhere" ([Content Rules §5.1](https://modrinth.com/legal/rules)).

| Field | Where it comes from |
|---|---|
| Name | `mod_name` in `gradle.properties`, and nothing else: no version, no "mod", no game name |
| Summary | `mod_description` in `gradle.properties`: one sentence on what the Library does |
| Description | `README.md`'s pitch, rewritten for players and pack authors: what it adds, and what they need to know before downloading |
| Licence | MIT (`LICENSE`), SPDX id `MIT`; a Library that carries other licences (Beltworks' CC-BY-4.0 assets) names them in the description |
| Source and issues links | `https://github.com/5thlayer/<repo>` and its `/issues` |
| Loader, game version | NeoForge and `minecraft_version`; each upload sets them per version |
| Environment | Client and Server, as `scripts/upload.py` sends |
| Dependencies | `modrinth_dependencies` and `curseforge_dependencies` in `gradle.properties` |

What the repo lacks: the icon, any gallery images, and the categories. The icon and gallery must show the Library honestly and must not be AI-generated on Modrinth ([§5.5, §6.2](https://modrinth.com/legal/rules)).

## Modrinth

Create a project from the Modrinth website. The API's create-project call lists the fields a project holds ([Create a project](https://docs.modrinth.com/api/operations/createproject/)):

- **Project type**: `mod`. Required.
- **Slug**: the project's URL, required, 3 to 64 characters from letters, digits and `!@$()`.+,"-'`. Use `mod_id`.
- **Title**: the name. Only the name of the project, "without any other unnecessary filler data" ([§5.2](https://modrinth.com/legal/rules)).
- **Summary** (`description` in the API): "no more than a sentence or two", without formatting and without repeating the title ([§5.3](https://modrinth.com/legal/rules)).
- **Description** (`body`): Markdown. It must make "a clear and honest attempt to describe" the project: what it does, why download it, and anything to know first ([§2](https://modrinth.com/legal/rules)). It needs an English version.
- **Categories** and **additional categories**: the project's tags.
- **Licence**: an SPDX id (`MIT`), with an optional licence URL.
- **Links**: issues, source, wiki, Discord and donation URLs, all optional. Every link must lead to a public, relevant page ([§5.4](https://modrinth.com/legal/rules)).
- **Icon**: PNG, JPG, BMP, GIF, WebP or SVG.
- **Gallery**: optional images, relevant and not misleading ([§5.5](https://modrinth.com/legal/rules)).
- **Contains AI-generated content**: a flag the project must carry when a substantial part of its code is AI output, when it has assets mainly made by AI, when its design or function relies on generative AI, or when its page (the description, say) relies on it ([§6.1](https://modrinth.com/legal/rules)). A Library written with Claude sets it. §6.2 also keeps a project that is "primarily or entirely a product of AI output" from public publication.

Environments (client and server) are no longer project settings: they belong to each version and carry over from the previous one ([Streamlined version creation](https://modrinth.com/news/article/streamlined-version-creation/)). `scripts/upload.py` names none on Modrinth, so the first version's environments are set on the website when it is uploaded by hand, or checked there after the first upload.

A draft project shows a publishing checklist in Project Settings, with the items that must be done before it can be submitted for review ([Streamlined version creation](https://modrinth.com/news/article/streamlined-version-creation/)). Modrinth aims to review a project in 24 to 48 hours, but it can take two to four weeks or longer ([Project review process](https://support.modrinth.com/en/articles/8793355-modrinth-project-review-process)).

The project ID `scripts/upload.py` needs is the `id` field of `https://api.modrinth.com/v2/project/<slug>` ([Get a project](https://docs.modrinth.com/api/operations/getproject/)), not the slug.

## CurseForge

Create the project at [authors.curseforge.com](https://authors.curseforge.com/#/projects/create/choose-game), choosing Minecraft. The form asks for ([Creating and submitting a project](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project), [Project submission guide and tips](https://support.curseforge.com/support/solutions/articles/9000199552-project-submission-guide-and-tips)):

- **Name**: required and unique; a taken name is rejected. No version numbers, no category word ("mod"), nothing excessively long. English only.
- **Summary**: required; "a one line explanation" of what the project does, not who made it.
- **Description**: required; what the mod does or adds, in enough detail. It may hold images, credits and install notes; donation and partner links stay few and at the bottom.
- **Project licence**: required; pick MIT from the dropdown, or Custom.
- **Class**: required; Mods.
- **Main category**: required; **additional categories**: up to 4.
- **Logo**: required; a PNG, at least 400×400 and square, the Library's own art, not a solid colour or the game's logo. The moderation policies advise against WebP ([Moderation policies](https://support.curseforge.com/en/support/solutions/articles/9000197279-project-submission-guidelines)).
- **Additional images**: optional for mods.

The project exists only once its first file is uploaded, so the first file goes up by hand in this form: the released jar from `~/.m2`, byte for byte (its sha256 is in the release tag), never a fresh `build/libs` jar, with that version's changelog section as its notes and the same release type, game version, loader, environments and related projects `scripts/upload.py` would send. A dependency nested in the jar, as Groundworks is in Beltworks, is not a related project; one the jar requires, named in `modrinth_dependencies` and `curseforge_dependencies`, is. `scripts/upload.py` uploads every later release; it refuses the hand-uploaded version once CurseForge has approved it, but can't see it while it waits for review, so nothing is uploaded to CurseForge until then. A project needs at least one Release file, not only betas, before it syncs to the CurseForge app ([Creating and submitting a project](https://support.curseforge.com/support/solutions/articles/9000197241-creating-and-submitting-a-project)); below 1.0 every upload is a beta unless `upload_release_type = release` in `gradle.properties` says otherwise, so a pre-1.0 Library that wants to reach the app sets it.

The project and each file go through automated and manual moderation. Support contacts the author within 48 to 72 hours if something must change ([Project submission guide and tips](https://support.curseforge.com/support/solutions/articles/9000199552-project-submission-guide-and-tips)). Moderation rejects re-uploads without changes and several similar, low-effort projects ([Moderation policies](https://support.curseforge.com/en/support/solutions/articles/9000197279-project-submission-guidelines)).

The project ID `scripts/upload.py` needs is the numeric one, not the slug.

## After both projects exist

1. Set `modrinth_project_id` and `curseforge_project_id` in `gradle.properties`, and the dependencies, if any, in `modrinth_dependencies` and `curseforge_dependencies`. The upload step is on from the next release.
2. Try it: `scripts/upload.py --dry-run <version>` for a released version prints what it would send, and contacts nothing.
3. Add the projects' links to `README.md`.
