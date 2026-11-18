# Modes d'emploi FK2Play 2.2

> **English:** [User_guide_FK2Play_all_versions.pdf](en/User_guide_FK2Play_all_versions.pdf) (RC3 to RC8) · [User_guide_FK2Play_RC3.pdf](en/User_guide_FK2Play_RC3.pdf) (illustrated, RC3).

> [!NOTE]
> **Version 2.2 stable** (interface en anglais ; décodage vidéo matériel depuis la 2.1), validée sur Civic Type R FK2 (RC3). Autres versions et modèles : vos [rapports de test](../../../issues/new?template=rapport-de-test.yml) sont les bienvenus.

| Guide | Pour qui |
|---|---|
| [Mode_emploi_FK2Play_RC3.pdf](Mode_emploi_FK2Play_RC3.pdf) | Honda Connect **RC3** (build `09.08C.00.xxxx`) : guide illustré pas à pas, 9 pages. |
| [Mode_emploi_FK2Play_toutes_versions.pdf](Mode_emploi_FK2Play_toutes_versions.pdf) | Toutes versions (RC3 à RC8), avec les chemins RC7/RC8. |

## Résumé en 8 étapes (RC3 à RC6.1)

1. **Vérifier la version** : HOME → Réglages → Android → À propos du dispositif → *Numéro de build*.

   ![Exemple RC3](images/exemple-version-rc3.jpg)

2. **Honda Permissions** (S_Mike, forum XDA, non fourni ici) : le renommer `HondaAppCenter_A1.apk`, le copier à la racine d'une clé USB FAT32, l'installer avec l'installateur d'APK du Honda Connect. En cas d'échec : réinitialisation usine d'Android puis nouvel essai.
3. **Débloquer** : Honda Permissions → *Allowed Installations* → appui long sur `HondaAppCenter_A1.apk` → crayon → *Sig digest sha256* = `PREINSTALL`.
4. **Installer FK2Play** : copier `HondaAppCenter_A1.apk` de la [release](../../../releases) sur la clé (seul fichier) et l'installer de la même façon.
5. **Droits** : Honda Permissions → *Process Controls* → `+` : Process/Package `com.fk2play`, Auth Type `THIRD_PARTY`, App Type `NAVI`, SoundOut / LastMode **ON** ; SoundInterrupt / SoundInterruptMute / ProcessKillTarget **OFF** (sinon le volume du volant ne répond plus).
6. **Test sans dongle** : lancer FK2Play et lire la ligne de diagnostic.

   ![Diagnostic](images/diagnostic.png)

7. **Brancher le dongle** directement (pas de hub), accepter l'accès USB. `Dongle initialise...` = réussite.
8. **iPhone** : Bluetooth → appareil du dongle → *Utiliser CarPlay*.
9. **Appels** (micro de la voiture inaccessible aux applis) : garde l'iPhone **connecté à HandsFreeLink** (Bluetooth d'origine du Honda) et coche *Réglages FK2Play → Appels via le Bluetooth de la voiture*. La musique reste en USB, et le Honda prend les appels avec son micro et les touches du volant. Sans cette option, déconnecte HandsFreeLink, sinon le Honda coupe le son des appels.

   ![Connexion (simulation)](images/carplay-connecte-simulation.png)

Réglages (icône « Réglages FK2Play » ou touche MENU) : résolution, images/s, mode nuit, son USB/Bluetooth, micro, Wi-Fi 5 / 2,4 GHz.

![Réglages](images/reglages.png)

**À ne pas faire** : mettre à jour le Honda Connect vers RC7/RC8 avant d'avoir tout installé (ou alors sans réinitialisation usine), mettre à jour les applis Honda ou le firmware du dongle, utiliser un hub USB.
