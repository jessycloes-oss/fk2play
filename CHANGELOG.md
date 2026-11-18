# Historique des versions

## 2.2 — 18 novembre 2026 (interface en anglais)

- **FK2Play suit la langue de l'autoradio** : français si le Honda Connect est réglé en français,
  **anglais** dans toutes les autres langues (réglages, messages d'état, diagnostic, test du micro,
  notification). Les réglages passent par `res/values` (anglais) et `res/values-fr` (français) ;
  les textes du code Java par `Tr.s(fr, en)`.
- **Modes d'emploi en anglais** : `docs/en/User_guide_FK2Play_RC3.pdf` et
  `docs/en/User_guide_FK2Play_all_versions.pdf` ; page « Nouveautés 2.2 » en tête des PDF français.
- Aucun changement dans la vidéo, le son, les touches du volant ni le protocole du dongle.
- *English*: FK2Play now follows the head unit language (French or English) and ships English user guides.

## 2.1 — 17 novembre 2026 (décodage vidéo matériel, version stable)

- **Validée en voiture** (Civic Type R FK2, RC3) : `Materiel : OMX.Nvidia.h264.decode 800x480`,
  30 images/s, 0 image jetée ; fluide, prise en charge immédiate, appels et retour depuis la radio OK.
- Regroupe les essais 2.1-test et 2.1-test2 ci-dessous. Merci à **MaKi83** (OpenDroidAuto) pour
  omxvideocodec et son accord.

### 2.1-test2 — 17 novembre 2026 (diagnostic du décodeur)

- *Diagnostic → Décodeur vidéo actif* affiche désormais le **dernier décodeur utilisé** (matériel ou
  logiciel, composant, images affichées/jetées, il y a combien de temps) : l'écran Réglages masque
  CarPlay, donc aucun décodeur ne tournait au moment de la lecture et la ligne indiquait « aucun ».

### 2.1-test — 16 novembre 2026 (décodeur vidéo matériel, à valider en voiture)

- **Décodage vidéo matériel** : l'image CarPlay (H.264) est décodée par la puce vidéo du Tegra 3
  (OMX, via libstagefright d'Android 4.0.4) et affichée directement, au lieu d'être décodée en logiciel
  par FFmpeg. Objectif : processeur beaucoup moins chargé, moins de chauffe, plus de marge.
  Code basé sur le module **omxvideocodec d'OpenDroidAuto, de MaKi83**, réutilisé avec son accord
  — merci !
- **Repli automatique sur FFmpeg** si le décodeur matériel n'est pas disponible ou ne produit pas
  d'image ; FFmpeg reste aussi utilisé pendant un appel (décodage sans écran) pour le retour
  instantané, puis le matériel reprend à l'image clé suivante.
- Réglages : *Vidéo → Décodeur vidéo* (Automatique / Logiciel uniquement) ; *Diagnostic → Décodeur
  vidéo actif* (composant, images affichées, images jetées, raison d'un éventuel repli).
- Compilation : `scripts/get_ics_sdk.sh` (en-têtes et bibliothèques AOSP 4.0.4, non publiés) puis
  `scripts/build_omx.sh`.

## 2.0 — 16 novembre 2026 (première version stable)

Première version qui n'est plus une bêta : validée en voiture (Civic Type R FK2, Honda Connect RC3,
dongle CPC200-CCPA, iPhone en CarPlay sans fil). Elle réunit le travail de la 1.7 (jamais publiée
séparément) :

- **Touches du volant** : **piste suivante / précédente** pour CarPlay, et le **volume reste géré par le
  Honda** (réactif, affiché au tableau de bord). FK2Play se signale au service volant du Honda Connect
  quand l'écran CarPlay est affiché ; le Honda lui envoie alors les touches piste. Méthode découverte
  par **MaKi83** (OpenDroidAuto) — merci ! Réglages de secours en *Diagnostic* (mode d'inscription A/B/C,
  inversions, journal des touches).
- **Fluidité** : image plus fluide et plus réactive quand l'autoradio est chargé, son qui ne freine plus
  l'image, rattrapage du retard, décodage sur deux cœurs du Tegra 3.
- **Appels** : retour immédiat sur CarPlay à la fin d'un appel.
- Validé en voiture : image, tactile, musique, appels via HandsFreeLink, CarPlay en arrière-plan,
  fluidité, touches du volant.

### Détail du développement de la 2.0 (ex-1.7, builds 8 à 15)

Objectif : une image plus fluide et plus réactive quand le Honda Connect est chargé (Tegra 3,
Android 4.0.4, logiciels Honda qui tournent en même temps), et un retour immédiat sur CarPlay à la
fin d'un appel. La 1.6 n'a pas été publiée (essai personnel abandonné).

- **Piste suivante / précédente au volant — service volant Honda** (build 10) : le Honda Connect ne
  transmet pas ces touches par les voies Android standard (vérifié en voiture avec le build 9). FK2Play
  s'inscrit désormais auprès du service volant du Honda Connect (SteeringMenuService) quand l'écran
  CarPlay est affiché. Méthode découverte par MaKi83 (OpenDroidAuto) — merci ! Réglage *Adresse
  volant Honda* (260 par défaut) ; en Diagnostic, *Service volant Honda* indique l'état de la connexion.
  Build 11 : les codes du Honda (65534 précédent, 65535 suivant) sont reconnus ; le volume
  Build 12 : en réalité le Honda envoie ces touches à l'écran FK2Play avec des codes **négatifs**
  (-65534 précédent, -65535 suivant, -65532/-65533 volume). Ils sont désormais reconnus ; le volume
  au volant règle le son CarPlay (options *Inverser volume + / -* et *Méthode du volume au volant*
  en Diagnostic).
  Build 13 : codes corrigés après essai (volume + -65535, volume − -65534, pistes -65532/-65533) ;
  le volume est rendu au Honda (volume réel et affichage au tableau de bord) ; option *Inverser
  suivant / précédent*.
  Build 14 : *Fenêtre volume* (3 s par défaut) — après un appui volume, le Honda garde le volume
  quelques secondes, les appuis suivants sont instantanés ; *Mode d'inscription volant* A/B/C
  (Diagnostic) pour chercher un mode où le Honda garde le volume.
  Build 15 : **mode C par défaut**, validé en voiture — le Honda garde le volume (instantané,
  affichage au tableau de bord) et les touches piste vont à CarPlay, sans délai.
- **Piste suivante / précédente au volant** (build 9, après le premier essai en voiture) : FK2Play se
  déclare lecteur multimédia auprès d'Android (focus audio, récepteur MEDIA_BUTTON, RemoteControlClient)
  pour recevoir les touches musique du volant, même quand l'écran CarPlay n'est pas affiché. Les codes
  MEDIA_NEXT/PREVIOUS, FAST_FORWARD/REWIND, CHANNEL_UP/DOWN et DPAD droite/gauche passent piste
  suivante / précédente. Option *Touches musique du volant* (cochée) ; en Diagnostic, *Dernières
  touches reçues* liste les 8 dernières touches et la commande envoyée.

- **Retour immédiat sur CarPlay à la fin d'un appel** : FK2Play n'attend plus que le Honda quitte
  son écran téléphone, il revient lui-même au premier plan (seulement si CarPlay était affiché avant
  l'appel, ou dans la minute précédente). Pendant l'appel, l'image CarPlay reste à jour en arrière-plan,
  donc elle s'affiche **instantanément** au retour. Option *Revenir à CarPlay dès la fin d'un appel*
  (cochée par défaut).
- **Retour instantané** sur l'écran CarPlay depuis une autre appli : la dernière image s'affiche tout
  de suite, puis l'image en direct ; l'image clé est redemandée si elle tarde.
- **Le son ne freine plus l'image** : l'écriture du son se fait dans un thread audio dédié. Avant, un
  tampon audio plein (rafale après une coupure Wi-Fi, léger décalage d'horloge iPhone/autoradio)
  bloquait la réception USB, donc l'image.
- **Rattrapage du retard** : si des images s'accumulent, seule la plus récente est affichée ; le
  retard se résorbe au lieu de durer. Fini le cercle vicieux de la 1.5 (une surcharge passagère la
  faisait tomber durablement à quelques images/s).
- **Fluidité automatique** : décodage allégé (filtre anti-blocs sauté) tant que l'autoradio n'arrive
  pas à suivre, retour à la normale après 3 s sans retard. Option désactivable.
- **Deux cœurs** : sur le Tegra 3 (4 cœurs), décodage et affichage tournent en parallèle.
- **Format d'affichage choisi sur l'appareil** : au démarrage, FK2Play mesure le coût réel d'affichage
  en 16 bits (RGB565) et en 32 bits (RGBX, conversion NEON) et garde le plus rapide.
- **Tactile** : l'envoi au dongle ne bloque plus l'écran, et les glissements en attente sont fusionnés
  (l'iPhone reçoit toujours la dernière position du doigt).
- Moins de travail pour le ramasse-miettes Android : plus d'allocation par image vidéo ou bloc audio.
- Diagnostic : réglages *Affichage sur un 2e cœur* et *Format d'affichage* (automatiques par défaut,
  utiles en cas de souci) ; mesures de réactivité dans le journal Android (`FK2Stat`).
- Banc d'essai reproductible : `sim/make_bench.py` + `sim/run_bench.sh` (émulateur Android 4.0.4,
  faux dongle en temps réel avec musique, coupures Wi-Fi, appel, pic de charge et gestes tactiles).

Mesures sur ce banc (émulateur volontairement surchargé ; 4 passages de la 1.5, 5 de la 1.7) :

| | 1.5 | 1.7 |
|---|---|---|
| Images affichées par seconde | 8,7 à 9,6 | 10 à 13 |
| Retard moyen de l'image | 159 à 172 ms | 74 à 78 ms |
| Images perdues (par 5 s) | ≈ 103 | 5 à 19 |
| Réception USB bloquée par le son (par 5 s) | 3,3 à 4,2 s | 0,02 à 0,03 s |
| Temps de l'écran pour un geste tactile | 2,0 à 2,8 ms | 0,2 à 0,9 ms |
| Retour sur CarPlay après un appel | 1,2 à 1,8 s *après* que le Honda a rouvert FK2Play | 0,2 à 0,3 s, sans attendre le Honda |
| Retour sur CarPlay depuis une autre appli | 0,3 à 0,7 s | 0,02 à 0,04 s (dernière image) |

L'émulateur n'a qu'un cœur et un affichage logiciel : les valeurs absolues ne sont pas celles du
Tegra 3, seul l'écart entre les versions compte. À confirmer en voiture.

## 1.5-beta — 15 novembre 2026 (CarPlay en arrière-plan)

Retour d'essai : quitter l'écran CarPlay (pour aller dans les réglages par exemple) coupait
FK2Play ; environ 20 s plus tard, FK2Play se rouvrait de force et fermait l'appli en cours.
Cause : la session avec le dongle était fermée en quittant l'écran ; privé de battement de cœur,
le dongle redémarrait et son rebranchement USB relançait FK2Play.

- **CarPlay continue en arrière-plan** : la connexion au dongle vit maintenant dans un service
  Android au premier plan (`CarPlayService`). Musique, appels, Siri et battement de cœur continuent
  pendant que l'on navigue dans le Honda Connect ; seule l'image est mise en pause. Au retour
  (icône FK2Play ou notification), l'image revient immédiatement, sans reconnexion.
- Les réglages qui doivent être envoyés au dongle (résolution, images/s, mode nuit, Wi-Fi, micro,
  son USB) déclenchent une reconnexion de quelques secondes au retour dans FK2Play ; les autres
  s'appliquent tout de suite.
- Si le dongle est débranché pendant que FK2Play est en arrière-plan, le service s'arrête ; le
  rebranchement relance FK2Play comme avant.

## 1.4-beta — 15 novembre 2026 (appels par le kit mains libres d'origine)

Sur la Civic Type R FK2 testée, le micro de la voiture n'est pas accessible aux applis Android
(le test du micro ne trouve rien) : il est câblé sur le module Bluetooth du Honda.

- **Nouvelle option « Appels via le Bluetooth de la voiture »** (idée de jessycloes-oss) :
  la musique et CarPlay restent par USB, et au début de chaque appel FK2Play demande au dongle de
  basculer le son sur le Bluetooth. La voiture prend alors l'appel avec son kit mains libres
  d'origine (HandsFreeLink) : micro, haut-parleurs et touches du volant d'origine. À la fin de
  l'appel, le Honda revient sur FK2Play, qui repasse le son en USB. L'iPhone doit rester
  connecté à HandsFreeLink.
- **Touche « raccrocher » du volant** : sur le Honda Connect, elle arrive comme RETOUR
  (code 4). Pendant un appel géré par FK2Play, elle raccroche au lieu de quitter l'appli.
- Test du micro : si aucun micro n'est trouvé, il conseille l'option ci-dessus.

## 1.3-beta — 15 novembre 2026 (appels téléphoniques)

Retours de l'essai sur Civic Type R FK2 (RC3) : le son du correspondant n'arrivait pas, le micro
ne transmettait rien, et les touches téléphone du volant ne raccrochaient plus.

- **Appels** : le micro envoie désormais au dongle un flux régulier toutes les 40 ms (du silence
  si le micro de la voiture ne donne rien). Avec « Utiliser le micro de la voiture » coché, l'appel
  ne se fige plus et le son du correspondant passe.
- **Nouveau : test du micro de la voiture** (Réglages → « Tester le micro de la voiture »). Essaie
  toutes les sources audio d'Android (MIC, VOICE_COMMUNICATION, VOICE_RECOGNITION, CAMCORDER,
  DEFAULT) à 16, 48 et 8 kHz pendant que vous parlez, retient celle qui vous entend le mieux, ou
  indique qu'aucun micro n'est accessible (il faut alors un dongle CCPA-MIC).
- **Touches téléphone du volant** : décrocher (CALL) et raccrocher (ENDCALL, ou touche mains libres
  pendant un appel) sont transmis à CarPlay.
- **Diagnostic** : option « Afficher le code des touches » pour identifier les touches du volant
  que FK2Play ne reconnaît pas encore.
- **Nouvelle icône « Réglages FK2Play »** dans la liste des applis : réglages accessibles même si la
  touche MENU est gardée par le Honda Connect.
- Lecture/pause au volant : commande « lecture/pause » unique de CarPlay.
- Modes d'emploi : déconnecter l'iPhone du Bluetooth d'origine du Honda (HandsFreeLink), sinon le
  Honda peut couper le son des appels CarPlay.

## 1.2-beta — 15 novembre 2026 (premier essai réel réussi)

**Premier essai dans une vraie voiture : réussi.** Civic Type R FK2, Honda Connect RC3
(build 09.08C.00.1123), dongle Carlinkit CPC200-CCPA, iPhone en CarPlay sans fil : image,
tactile, musique et commandes du volant fonctionnent.

- Correction : la fenêtre « Phase dongle : … » restait affichée en permanence par-dessus CarPlay.
  Les messages d'information du dongle (phase, firmware, appareil) ne s'affichent plus que
  3 secondes, et plus du tout dès que l'image CarPlay est là.
- Modes d'emploi : dans Honda Permissions → Process Controls, **SoundInterrupt et
  SoundInterruptMute doivent être sur OFF**, sinon les touches de volume du volant ne
  répondent plus (constaté lors de l'essai réel).

## 1.1-beta — 5 novembre 2026 (pré-version, jamais testée en voiture)

Première version publique, pour les **testeurs**. Validée uniquement sur un Honda Connect simulé
(émulateur Android 4.0.4 ARM, écran 800x480, faux dongle rejouant un flux CarPlay).

- Lecture USB avec délai maximal de 500 ms : évite un processus bloqué sur le noyau Linux 3.1 du Honda Connect si le dongle se tait.
- Lien USB fermé seulement après l'arrêt du thread de lecture (évite un plantage natif d'Android 4.0).
- Une seule session dongle à la fois, même en cas d'allers-retours rapides vers l'accueil.
- Diagnostic USB affiché si le dongle n'est pas détecté (« USB hote Android : oui/NON », périphériques vus).
- Micro : repli 48 kHz / 8 kHz, puis micro du dongle (modèle CCPA-MIC) si celui de l'autoradio est inaccessible.
- Décodeur vidéo en priorité normale : ne prend jamais le pas sur les services du Honda Connect.
- Touche MENU de l'autoradio → réglages.
- Mode simulation (faux dongle) réservé aux APK de test, inactif dans l'APK publié.
- APK signé avec la clé officielle du projet (empreinte dans le README).

Résultats de la simulation : 0 plantage, 0 blocage sur 4 minutes et 10 sessions, mémoire stable
(~12 Mo), 17/17 tests du protocole.
