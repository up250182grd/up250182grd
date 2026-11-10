package com.englishos.learning.api;

/** Niveles del Marco Común Europeo de Referencia. Los niveles de EnglishOS son estimaciones, no certificaciones. */
public enum CefrLevel {
    A1, A2, B1, B2, C1, C2;

    public CefrLevel next() {
        return this == C2 ? C2 : values()[ordinal() + 1];
    }

    public CefrLevel previous() {
        return this == A1 ? A1 : values()[ordinal() - 1];
    }

    public static CefrLevel min(CefrLevel a, CefrLevel b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
