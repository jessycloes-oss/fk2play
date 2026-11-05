# FK2Play — Apple CarPlay sur le Honda Connect (Android 4.0.4)

![statut](https://img.shields.io/badge/statut-B%C3%8ATA%20%E2%80%94%20jamais%20test%C3%A9%20en%20voiture-orange)
![android](https://img.shields.io/badge/Android-4.0.4%20(API%2015)-green)
![licence](https://img.shields.io/badge/licence-MIT-blue)

> [!WARNING]
> **Version bêta, jamais testée dans une voiture pour l'instant.** FK2Play n'a été validé que sur
> un Honda Connect **simulé** (émulateur Android 4.0.4 ARM + faux dongle). **Nous avons besoin de
> vos retours** : si vous l'essayez, réussi ou non, ouvrez un
> [rapport de test](../../issues/new?template=rapport-de-test.yml). L'appli et les modes d'emploi
> seront modifiés après les premiers essais réels. Utilisation à vos risques ; faites les essais
> **à l'arrêt**.

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

Dernière pré-version : page [**Releases**](../../releases).

| Fichier | Usage |
|---|---|
| `HondaAppCenter_A1.apk` | FK2Play **déjà renommé** pour l'installateur du Honda Connect : à copier tel quel à la racine d'une clé USB FAT32. |
| `FK2Play-1.1-beta.apk` | Le même fichier sous son vrai nom. |
| `Mode_emploi_FK2Play_toutes_versions.pdf` | Mode d'emploi pour toutes les versions RC (RC3 à RC8). |
| `Mode_emploi_FK2Play_RC3.pdf` | Mode d'emploi illustré, réservé aux Honda Connect **RC3** (build `09.08C.00.xxxx`). |

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

**Vérifié en simulation** (émulateur Android 4.0.4 ARM, 800x480, 1 Go, faux dongle rejouant un flux CarPlay H.264 + audio) :
0 plantage et 0 blocage sur 4 minutes et 10 sessions, mémoire stable (~12 Mo), image correcte en plein écran,
tactile transmis, reconnexion propre après débranchement ou trame corrompue, 17/17 tests du protocole.
Les 103 fonctions système utilisées par la bibliothèque vidéo existent dans le vrai Android 4.0.4.

**Pas encore vérifié** (il faut une vraie voiture) :
- que le port USB du Honda Connect est accessible aux applis (l'écran de diagnostic de FK2Play le dit immédiatement) ;
- la fluidité réelle du décodage sur le Tegra 3 ;
- l'accès au micro de la voiture pour Siri et les appels ;
- le comportement du son avec les réglages de Honda Permissions.

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
docs/               modes d'emploi (PDF) et images
```

## Licence et crédits

Code sous licence **MIT** ([LICENSE](LICENSE)). FFmpeg est intégré sous **LGPL 2.1+**
([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)). Protocole du dongle documenté par la communauté
(pycarplay de Colin Munro, node-carplay). Merci à S_Mike pour Honda Permissions et aux membres du fil XDA.

Apple, CarPlay et iPhone sont des marques d'Apple Inc. ; Honda et Honda Connect, de Honda Motor Co., Ltd.
FK2Play est un projet indépendant, **non affilié** à Apple, Honda ni Carlinkit.

---

## English

> [!WARNING]
> **Beta, never tested in a real car yet.** FK2Play has only been validated on a **simulated** Honda Connect
> (Android 4.0.4 ARM emulator + fake dongle). **We need your feedback**: please open a
> [test report](../../issues/new?template=rapport-de-test.yml), whether it works or not. Use at your own risk and test **while parked**.

FK2Play brings **Apple CarPlay** to the stock first-generation **Honda Connect** head unit (2015-2017 Civic incl. Type R FK2,
HR-V, CR-V, Jazz… running Android 4.0.4 on an NVIDIA Tegra 3) using a **Carlinkit CPC200-CCPA** ("Autokit") dongle.
The dongle holds Apple's authentication chip and runs CarPlay; the vendor's "Autokit" app needs Android 4.4 and won't
install on the Honda Connect. FK2Play replaces it: it drives the dongle over USB, decodes H.264 with FFmpeg in software
(no MediaCodec before Android 4.1), plays audio and sends touch and microphone back.

- **Download**: see [Releases](../../releases). `HondaAppCenter_A1.apk` is FK2Play already renamed for the Honda installer.
- **Install**: requires S_Mike's *Honda Permissions* (XDA, not included). Easy on RC3 to RC6.1 (rename trick); RC7/RC8 need a downgrade to RC6 or iRoot (see the XDA thread). Install guides are in French (PDF in Releases and `docs/`); the steps match the ones from the XDA thread.
- **First test**: launch FK2Play without the dongle and read the bracketed diagnostic line (`USB hote Android : oui/NON`), then plug the dongle directly (no hub). `Dongle initialise...` means the head unit talks to the dongle. Pair the iPhone over Bluetooth with the dongle and accept CarPlay.
- **Not verified yet**: USB host access for apps on the real unit, real decoding speed on the Tegra 3, car microphone access.
- **License**: MIT; FFmpeg under LGPL 2.1+. Not affiliated with Apple, Honda or Carlinkit.
