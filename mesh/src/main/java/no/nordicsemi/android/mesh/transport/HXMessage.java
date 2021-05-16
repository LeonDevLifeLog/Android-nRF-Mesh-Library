package no.nordicsemi.android.mesh.transport;

public class HXMessage extends ApplicationStatusMessage {
    public static final int HX_MESSAGE_OP_CODE = 0x54 | 0xC0;

    public HXMessage(AccessMessage message) {
        super(message);
        this.mParameters = message.getParameters();
    }

    @Override
    void parseStatusParameters() {
    }

    @Override
    public int getOpCode() {
        return HX_MESSAGE_OP_CODE;
    }
}
