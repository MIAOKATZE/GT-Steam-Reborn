package com.miaokatze.gtsr.api.compat;

/** A controller that owns its steam/input/output implementation. */
public interface ISelfManagedSteamMachine {

    boolean gtsr$allowsCoolingRegistration();
}
