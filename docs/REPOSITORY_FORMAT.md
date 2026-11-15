# GENJUR STREAM Repository and Plugin Format

GENJUR STREAM uses a small, data-only plugin contract. A plugin is a JSON catalog feed referenced by an HTTPS repository index. It is not executable Kotlin, JavaScript, or a CloudStream `.cs3` binary.

## 1. Repository index

The repository URL must return JSON in this shape:

```json
{
  "schemaVersion": 1,
  "name": "Example Licensed Catalog",
  "extensions": [
    {
      "id": "licensed-film-catalog",
      "name": "Licensed Film Catalog",
      "description": "Catalog feed for titles authorized for distribution.",
      "feedUrl": "https://catalog.example.org/genjur-feed.json"
    }
  ]
}
```

Each extension requires a unique `id`, a display `name`, and an HTTPS `feedUrl`. The app reads the index, lists extensions, and lets the user install or remove GENJUR feed extensions.

## 2. Catalog feed (GENJUR plugin payload)

The feed URL must return JSON with an explicit distribution-rights declaration:

```json
{
  "rights": {
    "redistributionAllowed": true,
    "licenseId": "YOUR-LICENSE-IDENTIFIER",
    "termsUrl": "https://catalog.example.org/terms",
    "attribution": "Catalog provider attribution"
  },
  "items": [
    {
      "title": "Example film",
      "description": "Synopsis supplied by the authorized provider.",
      "category": "Drama",
      "videoUrl": "https://media.example.org/authorized-title.mp4"
    }
  ]
}
```

The current feed reader requires:
- `rights.redistributionAllowed` to be `true`;
- a non-empty `rights.licenseId`;
- an HTTPS `rights.termsUrl`;
- a non-empty `rights.attribution`;
- an `items` array; each item must have a non-empty `title`;
- `videoUrl` may be empty for metadata-only catalog entries, otherwise it must be a valid HTTPS URL.

Use only catalog metadata, artwork, and playback URLs that the provider has authorized you to redistribute or display. A repository index is not itself proof of rights; each feed provider remains responsible for its license and terms.

## 3. How to test the included demo

Add this repository URL in GENJUR STREAM:

`https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/samples/repository-film-lokal.json`

Then:
1. Select the discovered “Katalog Film Indonesia (Demo)” extension and tap “Pasang ekstensi”.
2. Return to the home screen.
3. Tap “Muat katalog dari sumber”.
4. The three fictional demo titles should appear as metadata-only entries. They intentionally have no playback URL.

## 4. CloudStream compatibility boundary

GENJUR STREAM can read a CloudStream repository manifest with `manifestVersion: 1` and its `pluginLists` array. It fetches each HTTPS plugin-list JSON file and displays plugin metadata such as name, language, supported media types, and the HTTPS `.cs3` file URL.

This is discovery-only compatibility. GENJUR STREAM does not download, install, or execute CloudStream `.cs3` code. Those plugins require the CloudStream runtime and are not GENJUR STREAM feed extensions. The supported path is to provide a GENJUR JSON repository and catalog feed. The app does not execute downloaded scripts or scrape arbitrary website pages.
