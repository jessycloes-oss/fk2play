# Historique des versions

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
