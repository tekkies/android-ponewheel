package net.kwatts.powtools.model;

import net.kwatts.powtools.App;

public class Session {
    private int firmwareVersion;
    IUnlocker unlocker;

    public static Session Create(int firmwareVersion) {
        App.INSTANCE.session = new Session();
        getInstance().setFirmware(firmwareVersion);
        return getInstance();
    }

    public static Session getInstance() {
        return App.INSTANCE.session;
    }

    private void setFirmware(int firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public IUnlocker getUnlocker() {
        return UnlockerFactory.GetUnlocker(firmwareVersion);
    }
}
