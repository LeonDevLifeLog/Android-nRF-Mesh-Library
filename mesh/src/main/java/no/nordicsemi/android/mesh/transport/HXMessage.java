package no.nordicsemi.android.mesh.transport;

public class HXMessage extends ApplicationStatusMessage {
    /**
     * 0x54|0xc0 =0xD4
     * 0x74|0xc0=0xF4
     * 0x47|0xc0=0xC7
     * 0x06|0xc0=0xC6
     * 0x52|0xc0=0xD2
     */
    public static final int OP_CODE_CLIENT_SEND = 0xD45900;
    public static final int OP_CODE_CLIENT_SEND_WITHOUT_ACK = 0xF45900;
    public static final int OP_CODE_CLIENT_QUERY_LATEST_SENT= 0xC75900;
    public static final int OP_CODE_SERVER_ACK= 0xC65900;
    public static final int OP_CODE_SERVER_LATEST_RECEIVED= 0xD25900;

    public HXMessage(AccessMessage message) {
        super(message);
        this.mParameters = message.getParameters();
    }

    @Override
    void parseStatusParameters() {
    }

    @Override
    public int getOpCode() {
        return getMessage().getOpCode();
    }
}
