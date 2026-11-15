# GENJUR STREAM Repository Format

GENJUR STREAM repositories are HTTPS-hosted JSON indexes. The app reads metadata only; it does not download or execute extension code.

## Repository index

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

Each extension requires a unique `id`, a display `name`, and an HTTPS `feedUrl`.

## Catalog feed

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

Use only catalog metadata, artwork, and playback URLs that the provider has authorized you to redistribute or display. The app validates the rights declaration when loading a feed. A repository index is not itself proof of rights; each feed provider remains responsible for its license and terms.

The current app version supports JSON catalog feeds. It does not execute downloaded scripts, scrape arbitrary website pages, or treat a normal website homepage as a repository index.


## CloudStream repository discovery (read-only)

The app can also read a CloudStream repository manifest with `manifestVersion: 1` and its `pluginLists` array. It fetches each HTTPS plugin-list JSON file and displays plugin metadata such as name, language, supported media types, and the HTTPS `.cs3` file URL.

This is discovery-only compatibility. GENJUR STREAM does not download, install, or execute CloudStream `.cs3` code. Those plugins use the CloudStream runtime and are not GENJUR STREAM feed extensions. To play content in GENJUR STREAM, use a compatible JSON catalog feed with the rights declaration described above.
