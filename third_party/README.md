# Dependências de terceiros versionadas

## Spotify App Remote SDK 0.8.0

- Origem: https://github.com/spotify/android-sdk/releases/tag/v0.8.0-appremote_v2.1.0-auth (`spotify-app-remote-release-0.8.0.aar`)
- SHA-256: `b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198`
- Licença: Apache 2.0 (cabeçalho do `AndroidManifest.xml` do AAR)
- Por que aqui: o Spotify publica o App Remote só no GitHub, fora do Maven. Um repositório Maven local evita a dependência direta de `.aar`, que o AGP não aceita em módulos de biblioteca.
- O SDK usa o Gson em tempo de execução; a versão fica no version catalog (`gson`).

## Gravações de guitarra para os sons de erro e do menu (`sons/`)

Prévias em MP3 baixadas do Freesound, todas com licença **Creative Commons 0** (domínio público, conferida na página de cada uma em 2026-10-08). `scripts/montar_sfx_erro.py` monta com elas as seis variações `core/audio/src/main/res/raw/sfx_miss_*.wav`. `scripts/montar_sfx_menu.py` monta os sons dos botões do menu (`sfx_menu_*.wav`).

| Arquivo | Som no Freesound | Autor |
|---|---|---|
| `353605_muted_guitar_strum_chiller345.mp3` | https://freesound.org/people/chiller345/sounds/353605/ "Muted Guitar Strum" | chiller345 |
| `12006_dist_chr_gmin_pm_speedy.mp3` | https://freesound.org/people/SpeedY/sounds/12006/ "Dist_Chr_Gmin_pm.wav" | SpeedY |
| `12042_dist_sng_g_3rd_str_pm_speedy.mp3` | https://freesound.org/people/SpeedY/sounds/12042/ "Dist_sng_G_3rd_str_pm.wav" | SpeedY |
| `576575_guit_scratch_gazbooth.mp3` | https://freesound.org/people/gazbooth/sounds/576575/ "guit scratch.wav" | gazbooth |
| `483882_electric_guitar_string_pluck_noise_joao_janz.mp3` | https://freesound.org/people/Joao_Janz/sounds/483882/ "Electric Guitar String Pluck Noise 1_11" | Joao_Janz |
