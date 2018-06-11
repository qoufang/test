package keymantek.bletransmit;

/**
 * @author Administrator
 */
public interface OnBleReceiveListener {

    /**
     * 接收
     * @param data
     */
    void onBleReceive(byte[] data);
}
