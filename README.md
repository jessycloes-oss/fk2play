# FK2Play — Apple CarPlay sur le Honda Connect (Android 4.0.4)

![statut](https://img.shields.io/badge/version-2.2%20stable-brightgreen)
![android](https://img.shields.io/badge/Android-4.0.4%20(API%2015)-green)
![licence](https://img.shields.io/badge/licence-MIT-blue)

> [!NOTE]
> **Version 2.2 (18 novembre 2026) — interface en anglais.** FK2Play suit la langue de l'autoradio :
> français si le Honda Connect est en français, anglais sinon. Modes d'emploi en anglais dans la release.
> Rien ne change pour la vidéo, le son ni le dongle.
>
> **Version 2.1 (17 novembre 2026) — décodage vidéo matériel.** L'image CarPlay est maintenant décodée
> par la puce vidéo du Tegra 3 (30 images/s, 0 image perdue). Validée en voiture sur une Civic Type R
> FK2 (Honda Connect RC3, build 09.08C.00.1123) : CarPlay sans fil, image fluide, tactile, musique,
> appels via le kit mains libres d'origine et **touches du volant** (volume, piste suivante / précédente).
> Les autres versions (RC5 à RC8) et les autres modèles restent à confirmer : vos
> [rapports de test](../../issues/new?template=rapport-de-test.yml) sont les bienvenus.
> Utilisation à vos risques ; faites l'installation **à l'arrêt**.

*English version below.*

FK2Play affiche **Apple CarPlay** sur l'écran d'origine du **Honda Connect** de première génération
(Civic 9ᵉ génération et Civic Type R **FK2**, HR-V, CR-V, Jazz… 2015-2017, Android 4.0.4, NVIDIA Tegra 3),
grâce à un dongle **Carlinkit CPC200-CCPA** (« Autokit »).

- CarPlay exige une puce d'authentification Apple : c'est le **dongle** qui la contient et qui fait tourner CarPlay.
- Le fabricant fournit l'appli « Autokit » pour afficher l'image sur l'autoradio, mais elle exige Android 4.4 et **ne s'installe pas** sur le Honda Connect.
- **FK2Play remplace Autokit** : il pilote le dongle en USB, décode la vidéo H.264 avec FFmpeg (MediaCodec n'existe pas sous Android 4.0), joue le son et renvoie le tactile et le micro.

```
iPhone  ──Wi-Fi/Bluetooth──▶  dongle Carlinkit CCPA  ──USB──▶  Honda Connect (FK2Play)
```

## Télécharger

Dernière version : page [**Releases**](../../releases).

| Fichier | Usage |
|---|---|
| `HondaAppCenter_A1.apk` | FK2Play **déjà renommé** pour l'installateur du Honda Connect : à copier tel quel à la racine d'une clé USB FAT32. |
| `FK2Play-2.2.apk` | Le même fichier sous son vrai nom. |
| `Mode_emploi_FK2Play_toutes_versions.pdf` | Mode d'emploi pour toutes les versions RC (RC3 à RC8). |
| `Mode_emploi_FK2Play_RC3.pdf` | Mode d'emploi illustré, réservé aux Honda Connect **RC3** (build `09.08C.00.xxxx`). |
| `User_guide_FK2Play_all_versions.pdf` / `User_guide_FK2Play_RC3.pdf` | Les mêmes modes d'emploi **en anglais**. |

Empreinte SHA-256 du certificat de signature officiel (pour vérifier qu'un APK vient bien de ce projet) :
`8D:F7:C2:BD:D0:F1:B9:3E:DF:61:1F:5D:22:06:26:4C:23:65:39:41:EE:7E:39:A9:C8:54:B7:27:D6:CC:29:26`

## Ce qu'il faut

- Un Honda Connect 1ʳᵉ génération sous **Android 4.0.4**.
- Un dongle **Carlinkit CPC200-CCPA** (modèle « Autokit » pour autoradio Android). Les modèles 2Air / 5.0 / CP2A / U2W / Mini ne conviennent **pas**. Ne mettez pas à jour son firmware.
- L'outil communautaire **Honda Permissions** (S_Mike, forum XDA), **non fourni ici**.
- Un iPhone sous iOS 10 ou plus récent.

## Compatibilité des versions du Honda Connect

| Build Android (Réglages → Android → À propos) | Version | Installation de FK2Play |
|---|---|---|
| `09.08C.00.xxxx` | RC3 | Simple : renommage + Honda Permissions ([guide RC3](docs/)) |
| `0C.091.xx.xxxx` | RC5 | Simple (même méthode) |
| `0E.094.00.xxxx` | RC6 / RC6.1 | Simple (même méthode) |
| `0F.095.01.xxxx` | RC7 | Difficile : retour en RC6 ou iRoot (voir fil XDA) |
| `10.096.00.xxxx` | RC8 | Difficile : retour en RC6 ou iRoot (voir fil XDA) |

Correspondances relevées sur le fil XDA « Honda Connect Android System ».

## Ce qui a été vérifié (et ce qui ne l'a pas été)

**Vérifié en voiture** (15 nov. 2026, Civic Type R FK2, RC3, dongle CPC200-CCPA, iPhone sans fil) :
le port USB du Honda Connect est bien accessible à l'appli, CarPlay s'affiche, tactile, musique et
volume au volant fonctionnent (avec SoundInterrupt et SoundInterruptMute sur OFF dans Process Controls).

**Vérifié en simulation** (émulateur Android 4.0.4 ARM, 800x480, 1 Go, faux dongle rejouant un flux CarPlay H.264 + audio) :
0 plantage et 0 blocage sur 4 minutes et 10 sessions, mémoire stable (~12 Mo), image correcte en plein écran,
tactile transmis, reconnexion propre après débranchement ou trame corrompue, 17/17 tests du protocole.
Les 103 fonctions système utilisées par la bibliothèque vidéo existent dans le vrai Android 4.0.4.

**Appels** : le micro de la Civic n'est pas accessible aux applis Android. Les appels passent donc
par le **kit mains libres d'origine** : cochez *Réglages FK2Play → Appels via le Bluetooth de la voiture*
et laissez l'iPhone connecté à HandsFreeLink. La musique reste en USB, et les appels utilisent le micro
et les touches du volant d'origine (validé en voiture en mode « tout Bluetooth » ; mode mixte de la
1.4 à confirmer). Autre solution : un dongle **CCPA-MIC** (micro intégré).

**Arrière-plan** (1.5, validé en voiture) : quitter l'écran CarPlay ne coupe plus rien. Musique, appels
et connexion continuent pendant que vous utilisez les réglages ou une autre appli ; l'icône FK2Play (ou sa
notification) ramène l'image.

**Décodage vidéo matériel** (2.1, validé en voiture) : l'image H.264 est décodée par la puce vidéo
du Tegra 3 (`OMX.Nvidia.h264.decode`) et affichée directement : 30 images/s, processeur soulagé. Repli
automatique sur le décodage logiciel (FFmpeg) si le matériel n'est pas disponible. Vérification :
*Réglages FK2Play → Diagnostic → Décodeur vidéo actif* (dernier décodeur utilisé, images affichées).
Code basé sur le module **omxvideocodec d'OpenDroidAuto de MaKi83**, réutilisé avec son accord — merci !

**Fluidité** (2.0, validé en voiture) : image plus fluide et plus réactive quand l'autoradio est chargé,
retour immédiat sur CarPlay à la fin d'un appel (sans attendre l'écran téléphone du Honda). Détails et
mesures dans le [CHANGELOG](CHANGELOG.md).

**Touches du volant** (2.0, validé en voiture) : sur l'écran CarPlay, les touches piste du volant passent
au morceau suivant / précédent, et le volume reste géré par le Honda (affiché au tableau de bord). La touche
SOURCE ouvre toujours les sources du Honda. En cas de souci : *Réglages FK2Play → Diagnostic* (« Mode
d'inscription volant » : C par défaut, A en secours ; « Inverser suivant / précédent » ; « Dernières touches
reçues »). Les touches piste ne pilotent CarPlay que lorsque son écran est affiché.

**Langue** (2.2) : réglages, messages d'état, diagnostic et test du micro sont en français si le Honda
Connect est réglé en français, et en anglais dans toutes les autres langues (`res/values` = anglais,
`res/values-fr` = français ; textes du code Java dans `Tr.s(fr, en)`).

**Pas encore vérifié** :
- les versions RC5 à RC8 et les autres modèles (HR-V, CR-V, Jazz…) ;
- les longs trajets (stabilité sur la durée, chauffe).

## Signaler un essai

Ouvrez un [**rapport de test**](../../issues/new?template=rapport-de-test.yml) : modèle de voiture, numéro de build,
ligne de diagnostic affichée avec et sans dongle, ce qui fonctionne. Une photo de la fiche de résultats (dernière
page du mode d'emploi) suffit. Si FK2Play affiche `vus : xxxx:yyyy` avec un autre identifiant que `1314:1520` ou
`1314:1521`, indiquez-le : le modèle de dongle sera ajouté.

## Compiler

Le plus simple : **GitHub Actions** compile FFmpeg, la bibliothèque native et un APK de test à chaque envoi
(onglet *Actions*, artefact `FK2Play-apk`, signé avec une clé de test).

En local (Linux / WSL) :

```bash
export NDK=~/android-ndk-r17c        # r17c : dernier NDK qui cible encore l'API 15
./scripts/build_native.sh            # FFmpeg n4.4.4 minimal + libfk2video.so
./scripts/run_protocol_tests.sh      # tests du protocole (Java pur, sans matériel)
gradle :app:assembleRelease          # JDK 17+, Android SDK 34
```

Sur Ubuntu récent, le clang du NDK r17c réclame `libtinfo.so.5` : créez un lien vers `libtinfo.so.6`.
La signature officielle utilise un fichier `keystore.properties` local, jamais publié (voir `app/build.gradle`).

**Simulation sans voiture** : `sim/make_stream.py` fabrique un faux flux de dongle à partir d'un fichier H.264 ;
un APK *debug* lancé avec `adb shell am start -n com.fk2play/.CarPlayActivity --es sim_file /data/local/tmp/fk2sim.bin`
le rejoue (voir `sim/run_sim.sh`). Ce mode est désactivé dans l'APK publié.

## Arborescence

```
app/src/main/java/com/fk2play/
  protocol/   protocole du dongle (Java pur, testé sans matériel)
  usb/        transport USB host (délais de lecture bornés)
  video/      file d'images + décodeurs FFmpeg (JNI) / MediaCodec (Android ≥ 4.1)
  audio/      sortie son + micro (avec replis)
  sim/        faux dongle pour les tests (APK debug uniquement)
app/src/main/jni/   fk2video.c (FFmpeg → ANativeWindow), Android.mk, Application.mk
scripts/            build_ffmpeg.sh, build_native.sh, run_protocol_tests.sh
sim/                générateur de flux et script de simulation
test/               tests du protocole + banc de test du décodeur natif
docs/               modes d'emploi (PDF) et images ; docs/en/ : guides en anglais
```

## Licence et crédits

Code sous licence **MIT** ([LICENSE](LICENSE)). FFmpeg est intégré sous **LGPL 2.1+**
([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). Protocole du dongle documenté par la communauté
(pycarplay de Colin Munro, node-carplay). Merci à S_Mike pour Honda Permissions, à MaKi83 (OpenDroidAuto)
pour la découverte du service volant du Honda Connect et pour son décodeur vidéo matériel (omxvideocodec,
réutilisé avec son accord), et aux membres du fil XDA.

Apple, CarPlay et iPhone sont des marques d'Apple Inc. ; Honda et Honda Connect, de Honda Motor Co., Ltd.
FK2Play est un projet indépendant, **non affilié** à Apple, Honda ni Carlinkit.

---

## English

> [!NOTE]
> **Version 2.2 (Nov 18, 2026) — English interface.** FK2Play now follows the head unit language: everything
> (settings, status messages, diagnostics, microphone test) is in **English**, or in French when the Honda Connect
> is set to French. **English user guides** (PDF) are included in the release.
> Built on 2.1 (hardware video decoding on the Tegra 3, 30 fps, based on MaKi83's omxvideocodec from OpenDroidAuto,
> reused with his permission), validated in a Civic Type R FK2 (Honda Connect RC3): wireless CarPlay, smooth video,
> touch, music, calls through the stock hands-free kit and **steering-wheel buttons**.
> Other RC versions and models still need confirmation: [test reports](../../issues/new?template=rapport-de-test.yml) are welcome.
> Use at your own risk and install **while parked**.

FK2Play brings **Apple CarPlay** to the stock first-generation **Honda Connect** head unit (2015-2017 Civic incl. Type R FK2,
HR-V, CR-V, Jazz/Fit… running Android 4.0.4 on an NVIDIA Tegra 3) using a **Carlinkit CPC200-CCPA** ("Autokit") dongle.
The dongle holds Apple's authentication chip and runs CarPlay; the vendor's "Autokit" app needs a newer Android and won't
install on the Honda Connect. FK2Play replaces it: it drives the dongle over USB, decodes H.264 with the Tegra 3 hardware decoder
(OMX through libstagefright; FFmpeg software fallback, since there is no MediaCodec before Android 4.1), plays audio and sends touch and microphone back.

### Download

[Releases](../../releases) page:

| File | Use |
|---|---|
| `HondaAppCenter_A1.apk` | FK2Play **already renamed** for the Honda Connect installer: copy it as is to the root of a FAT32 USB stick. |
| `FK2Play-2.2.apk` | The same file under its real name. |
| `User_guide_FK2Play_all_versions.pdf` | English user guide for all RC versions (RC3 to RC8). |
| `User_guide_FK2Play_RC3.pdf` | Illustrated English guide for **RC3** head units (build `09.08C.00.xxxx`). |

Official signing certificate SHA-256: `8D:F7:C2:BD:D0:F1:B9:3E:DF:61:1F:5D:22:06:26:4C:23:65:39:41:EE:7E:39:A9:C8:54:B7:27:D6:CC:29:26`

### Quick start

1. **Honda Permissions** (S_Mike, XDA, not included): easy on RC3 to RC6.1 (rename it `HondaAppCenter_A1.apk`, install it from a USB stick, then set *Allowed Installations → Sig digest sha256* to `PREINSTALL`). RC7/RC8 need a rollback to RC6 or iRoot (see the XDA thread).
2. **Install FK2Play**: copy `HondaAppCenter_A1.apk` from the release (alone) to the stick and install it the same way.
3. **Honda Permissions → Process Controls → +**: `com.fk2play`, Auth Type `THIRD_PARTY`, App Type `NAVI`, SoundOut / LastMode **ON**, SoundInterrupt / SoundInterruptMute / ProcessKillTarget **OFF** (otherwise the steering-wheel volume stops working).
4. **First test**: launch FK2Play without the dongle and read the bracketed line (`Android USB host: yes/NO`), then plug the dongle in directly (no hub). `Dongle initialized, waiting for the iPhone...` means the head unit talks to the dongle.
5. **iPhone**: Settings → Bluetooth → the dongle (`AutoKit-xxxx`) → accept *Use CarPlay*.

### Good to know

- **Phone calls**: the car microphone is not reachable by Android apps. Tick *FK2Play settings → Calls through the car Bluetooth* and keep the iPhone connected to the Honda's HandsFreeLink: music stays on USB, calls use the original hands-free kit.
- **Steering wheel**: on the CarPlay screen, the track buttons skip next / previous and volume stays handled by the Honda (thanks to MaKi83's discovery of the Honda steering service). Fallbacks in *Settings → Diagnostics*.
- **Background**: leaving the CarPlay screen keeps music, calls and the connection running; tap the FK2Play icon to come back.
- **Hardware decoding**: check *Settings → Diagnostics → Active video decoder* (`Hardware: OMX.Nvidia.h264.decode`). If the picture looks wrong, set *Video decoder* to *Software only (FFmpeg)*.
- **Language**: follows the head unit language (HOME → Settings → Android → Language).
- **Never** accept a Honda Connect update: from RC7 app installation is blocked.
- **Not verified yet**: RC5–RC8 and other models, long trips.
- **License**: MIT; FFmpeg under LGPL 2.1+. Not affiliated with Apple, Honda or Carlinkit.
