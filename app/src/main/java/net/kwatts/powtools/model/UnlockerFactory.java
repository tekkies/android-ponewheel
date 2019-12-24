package net.kwatts.powtools.model;

public class UnlockerFactory {
    public static IUnlocker GetUnlocker(int firmwareVersion) {
        IUnlocker unlocker=null;
        if(firmwareVersion <= 4033)
        {
            unlocker = new V1Unlocker();
        }
        if(unlocker == null && firmwareVersion <= 4141)
        {
            unlocker = new V2Unlocker();
        }
        if(unlocker == null)
        {
            unlocker = new V3Unlocker();
        }
        return unlocker;
    }
}
