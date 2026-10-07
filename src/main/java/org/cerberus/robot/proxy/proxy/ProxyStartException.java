package org.cerberus.robot.proxy.proxy;

/**
 * The proxy engine could not be started (or stopped right after it was). The message is meant to be
 * shown to the caller: it carries the reason and the last lines the engine printed.
 */
public class ProxyStartException extends RuntimeException {

    public ProxyStartException(String message) {
        super(message);
    }
}
