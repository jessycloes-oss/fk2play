# Historique des versions

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
