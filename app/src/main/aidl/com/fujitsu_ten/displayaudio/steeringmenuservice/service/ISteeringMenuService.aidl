// Interface du service volant du Honda Connect (Fujitsu Ten), declaree ici pour l'interoperabilite.
// L'ordre des methodes fixe les numeros de transaction Binder : ne pas le modifier.
// Signatures relevees par MaKi83 (projet OpenDroidAuto) ; merci a lui.
package com.fujitsu_ten.displayaudio.steeringmenuservice.service;

import com.fujitsu_ten.displayaudio.steeringmenuservice.service.ISteeringMenuServiceCallback;

interface ISteeringMenuService {
    void doModeMgrRequest(int mode);
    IBinder getIMidIf();
    void notifySteeringMenuDispMode(int address, int dispMode);
    void registerCallback(ISteeringMenuServiceCallback cb);
    void registerCallbackEx(ISteeringMenuServiceCallback cb, int address);
    void requestFinishView();
    void requestFinishViewEx(boolean anime);
    void startAudioLauncher();
    void startAudioLauncherPopup();
    void unregisterCallback(ISteeringMenuServiceCallback cb);
    void unregisterCallbackEx(ISteeringMenuServiceCallback cb, int address);
}
