// Rappel du service volant du Honda Connect (voir ISteeringMenuService.aidl).
package com.fujitsu_ten.displayaudio.steeringmenuservice.service;

interface ISteeringMenuServiceCallback {
    boolean onFinishView(boolean flag, boolean anime);
    void onShowView();
    boolean onSteeringSWDown(int keyCode);
}
