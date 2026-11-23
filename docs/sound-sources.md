# Free sound sources for Nightfall

Research checked 22 November 2026. This is a licensing screen, not legal advice.

## Recommendation

Prefer **CC0** recordings (or recordings made for Nightfall) and bundle them in the app. CC0 permits copying, modifying, incorporating and redistributing a work in any medium, for any purpose, including commercial use; attribution is not a condition. [CC0 deed](https://creativecommons.org/publicdomain/zero/1.0/deed.en) · [CC0 legal code](https://creativecommons.org/publicdomain/zero/1.0/legalcode.en)

This supports offline playback and app distribution to one or many users. Trimming, encoding, mixing, and looping are allowed. Keep a source-and-license record for each actual file; credit is optional for CC0, but useful provenance. CC0 does not clear other people’s privacy, publicity, trademark, or other rights, and the affirmer gives no warranty of ownership. Avoid recordings with discernible speech, music, or other third-party material unless those rights are separately cleared.

## Seven-sound shortlist

These are source-page candidates, not final audio selections. Listen and assess loop seams, quality, and suitability before shipping.

| Sound | Candidate and source-page license | Practical notes |
| --- | --- | --- |
| Rain | [Rain (loopable), OpenGameArt](https://opengameart.org/content/rain-loopable) — listed as CC0; four 25–45 second loops. | Promising ready-made loops. |
| Ocean | [Sea Waves, BigSoundBank](https://bigsoundbank.com/sea-waves-s0698.html) — explicitly CC0; 2:47 stereo recording, downloadable in multiple formats. | Trim or crossfade for a seamless loop. |
| Fan | [“(defect) motor loop,” OpenGameArt](https://opengameart.org/content/defect-motor-loop) — listed as CC0; uploader describes it as their graphics-card fan. | Rights look suitable, but it may not be a good sleep-quality fan bed. **Safer practical choice: make a clean, controlled recording of your own fan** (no voices/music in the recording), or get an explicit CC0/commissioned recording. |
| Fireplace | [Fireplace Sound loop, OpenGameArt](https://opengameart.org/content/fireplace-sound-loop) — listed as CC0; uploader says credit is appreciated but not required. | Check for abrupt transitions and trim/loop if needed. |
| Train | [Train car, BigSoundBank](https://bigsoundbank.com/train-car-s2727.html) — explicitly CC0; 4:11 stereo train-interior soundscape. BigSoundBank’s [license page](https://bigsoundbank.com/licenses.html) expressly allows redistribution in any format, adaptation, commercial use, and use without restrictions or required credit. | Listen for announcements or intelligible voices before use. |
| Wind | [wind1, OpenGameArt](https://opengameart.org/content/wind1) — listed as CC0; five one-minute synthesized wind sounds, described as likely loopable. | Synthetic rather than field-recorded; audition the loop. |
| Forest | [Park ambiences, OpenGameArt](https://opengameart.org/content/park-ambiences) — listed as CC0; includes a copse-of-trees/birds recording and other outdoor ambiences. | The page lists large WAVs (the birds recording is about 86 MB); trim and encode for app size. Verify the chosen track is the forest-like one. |

OpenGameArt’s sound pages identify their individual uploads and licenses; the CC0 terms above establish the broad reuse permissions. The uploader’s license declaration is still an assertion, not a title warranty. Check the exact selected asset page and preserve a dated copy/screenshot or other evidence of its license when acquiring it.

## Samples installed in the browser demo

Two BigSoundBank MP3s are bundled in `prototype/audio/` and copied into the LAN demo. The browser uses native audio controls and **does not loop these samples**; their loop boundaries have not been approved for Nightfall playback. The source pages identify both as CC0 and BigSoundBank’s license terms permit copying, redistribution, adaptation, and commercial use in any format, including apps.

| Local file | Source | Size / duration | SHA-256 |
| --- | --- | --- | --- |
| `rain-on-puddle.mp3` | [Rain on Puddle, Joseph Sardin](https://bigsoundbank.com/rain-on-puddle-s1290.html) | 3.0 MB / 1:18, mono 48 kHz | `aa5a6824c92440431ec38b3de692a941b61fdfa3c29c69b14307651000269360` |
| `sea-waves.mp3` | [Sea Waves, Joseph Sardin](https://bigsoundbank.com/sea-waves-s0698.html) | 6.5 MB / 2:47, stereo 48 kHz | `caa64ec0ffd4319f016459b3295d3f8c6eaa5209d6e95c9c5cfd897c02797a22` |

The files are unchanged downloads from the sounds’ official BigSoundBank player paths. Attribution is not required by the stated license; the demo credits each creator and links back to the source page. Keep these as examples until their sound quality and loop edits are reviewed for the app.

## Other sources screened

- **Freesound:** The official [FAQ](https://freesound.org/help/faq/) says each uploader chooses a license; current options include CC0, CC BY, and CC BY-NC. It says CC0 can be used broadly; BY requires creator credit; NC disallows commercial use. Therefore filter for **CC0** first. CC BY can allow redistribution and adaptations, but requires appropriate credit, a license link, and notice of changes ([CC BY 4.0 deed](https://creativecommons.org/licenses/by/4.0/deed.en)). Avoid NC unless the rights holder separately grants permission covering Nightfall’s actual distribution/business model. Freesound also cautions that user uploads may include unauthorized material, so per-file review remains necessary.
- **Pixabay:** Its official [license summary](https://pixabay.com/service/license-summary/) allows free use, modification, and no attribution, but prohibits selling or distributing content on a “Standalone” basis. It does not explicitly resolve whether bundled sleep sounds—central to this app’s function—are allowed; **do not rely on it without written clarification/permission**.
- **Mixkit:** Its [Sound Effects Free License](https://mixkit.co/license/modal/sfxFree/) permits use in a larger, different End Product and names video games, films, and other media, but bars redistribution as a standalone item, tool, template, or source file. An offline sound app distributes audio assets as a core feature; whether that is an allowed End Product is **unclear from the stated examples**. Avoid absent written permission specifically covering bundled audio in an iOS app.

## Per-file release checklist

1. Check the **individual asset page**, not just a catalog label or search result; confirm the license is CC0 (or retain a separate written grant).
2. Save the page URL, creator/uploader, title, download date, displayed license, and a copy/screenshot with the release records.
3. Listen for speech, music, or recognizable third-party material; reject or separately clear anything questionable.
4. Record any edits (trim, loop, mix, encode). CC0 requires no attribution, but retain provenance and optionally credit creators.
5. Recheck the exact audio file and license before each release; catalog terms, asset availability, and individual declarations can change.
