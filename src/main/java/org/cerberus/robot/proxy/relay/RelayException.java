package org.cerberus.robot.proxy.relay;

/**
 * Failure of the relay itself (as opposed to a response of the target, which is always relayed
 * as a 200). Serialized as {"error": message, "code": code} with the given HTTP status.
 */
public class RelayException extends Exception {

    private final int status;
    private final String code;

    public RelayException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
