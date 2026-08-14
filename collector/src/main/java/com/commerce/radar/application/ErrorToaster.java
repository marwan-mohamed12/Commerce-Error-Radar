package com.commerce.radar.application;

/**
 * Native Windows toast. No-op on other OSes or when the shell cannot toast.
 */
public interface ErrorToaster {

    boolean available();

    void show(ErrorNotification notification);
}
