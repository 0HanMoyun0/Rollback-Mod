package com.taobao.koi.rollbackmod.core;

public enum CoreType {
    DISCONNECTION("disconnection_core", false),
    CHRONOS("chronos_core", false),
    MOLTING("molting_core", false),
    TOWER("tower_core", false),
    SAND("sand_core", true),
    CAUSALITY("causality_core", true),
    MYRIAD("myriad_core", true),
    STASIS("stasis_core", true);

    private final String registryName;
    private final boolean markingCore;

    CoreType(String registryName, boolean markingCore) {
        this.registryName = registryName;
        this.markingCore = markingCore;
    }

    public String registryName() {
        return registryName;
    }

    public boolean isMarkingCore() {
        return markingCore;
    }
}
