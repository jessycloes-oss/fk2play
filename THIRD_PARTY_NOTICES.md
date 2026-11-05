# Composants tiers / Third-party components

## FFmpeg (libavcodec, libavutil, libswscale) — LGPL 2.1 ou ultérieure

FK2Play intègre une version minimale de **FFmpeg n4.4.4** (décodeur H.264 + conversion d'image),
liée statiquement dans `libfk2video.so`. FFmpeg est compilé **sans** `--enable-gpl` ni
`--enable-nonfree` : il reste sous licence **GNU LGPL 2.1 ou ultérieure**
(texte complet : [`licenses/FFmpeg-LGPL-2.1.txt`](licenses/FFmpeg-LGPL-2.1.txt)).

Conformément à la LGPL :
- les sources de FFmpeg sont disponibles sur <https://ffmpeg.org> et <https://github.com/FFmpeg/FFmpeg> (tag `n4.4.4`) ;
- les options de compilation exactes sont dans [`scripts/build_ffmpeg.sh`](scripts/build_ffmpeg.sh) ;
- tout le code de FK2Play est publié ici avec ses scripts, ce qui permet de recompiler
  `libfk2video.so` avec une autre version de FFmpeg (voir « Compiler » dans le README).

FFmpeg is a trademark of Fabrice Bellard, originator of the FFmpeg project.

## Protocole du dongle Carlinkit

Le protocole USB du dongle a été documenté par la communauté, notamment par les projets
**pycarplay** (Colin Munro) et **node-carplay**. FK2Play en est une réimplémentation
indépendante en Java ; aucun code de ces projets n'est inclus.

## Outils non inclus

**Honda Permissions** (auteur : S_Mike, forum XDA) est nécessaire pour installer FK2Play sur
le Honda Connect, mais il n'est **ni inclus ni redistribué** ici. Il se récupère auprès de son
auteur (fil XDA « Honda Connect Android System »).

## Marques

Apple, CarPlay et iPhone sont des marques d'Apple Inc. Honda et Honda Connect sont des marques
de Honda Motor Co., Ltd. Carlinkit est une marque de son propriétaire. FK2Play est un projet
indépendant, **non affilié** à ces sociétés et non approuvé par elles.
