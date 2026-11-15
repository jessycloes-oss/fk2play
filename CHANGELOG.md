# Historique des versions

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
