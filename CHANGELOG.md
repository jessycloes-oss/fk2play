# Historique des versions

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
