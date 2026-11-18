# OmegaT Baidu Translate plugin

An [OmegaT](https://omegat.org/) machine translation connector for
[Baidu Translate](https://fanyi-api.baidu.com/) (百度翻译).

## Features

- Uses the Baidu general text translation API
  (`https://fanyi-api.baidu.com/api/trans/vip/translate`).
- APP ID and secret key are stored through OmegaT's credential manager, so they
  can be protected with the OmegaT master password.
- Supports the "store credentials for this session only" option.
- Language mapping for all language pairs documented by Baidu (including
  `jp`, `kor`, `fra`, `spa`, `cht`, etc.).
- Caches translations through OmegaT's standard MT cache layer.
- Handles multi-line segments by joining Baidu's line-based results.
- Truncates queries longer than Baidu's 6000-byte request limit.
- Uses Jackson for JSON parsing (provided by OmegaT's bundled libraries).

## Requirements

- OmegaT 6.1.1 or later.
- Java 11 or later (the plugin is compiled for Java 11).
- A Baidu Translate API account with an APP ID and secret key. Apply at
  <https://fanyi-api.baidu.com/>.

## Build

```shell
./gradlew build
```

The plugin jar is written to `build/libs/` and the distribution zip to
`build/distributions/`.

## Install

1. Copy the plugin jar (or the jar from the distribution zip) to the OmegaT
   plugins directory:
   - Windows: `%APPDATA%\OmegaT\plugins\`
   - Linux/macOS: `~/.omegat/plugins/`
   - Or the `plugins` directory of the OmegaT installation.
2. Restart OmegaT.

## Configure

1. Open **Options > Machine Translation**.
2. Select **Baidu Translate** and click **Configure**.
3. Enter the **APP ID** and **secret key** from the Baidu Translate console.
4. Confirm with **OK**, then enable **Baidu Translate** in the machine
   translation menu or options.

## Development

Source layout:

- `BaiduTranslator` — plugin entry point and MT connector.
- `BaiduTranslateClient` — HTTP request, signature and Jackson response
  handling.
- `BaiduLanguages` — OmegaT to Baidu language code mapping.
- `BaiduTranslatorConfigDialog` — settings dialog based on OmegaT's
  `MTConfigDialog`.

Run the unit tests with:

```shell
./gradlew test
```

## License

This plugin is distributed under the GNU General Public License version 3 or
later. See `COPYING`.
