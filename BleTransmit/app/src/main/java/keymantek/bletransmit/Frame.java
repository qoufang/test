package keymantek.bletransmit;

public class Frame {

    public final static int CMDStartPos = 7;
    public final static int DataStartPos = 11;
    public final static byte EndFlag = (byte) 0xE7;
    public final static int EndStartPosNotAddDataLen = 12;
    public final static int LenStartPos = 9;
    public final static byte StartFlag = (byte) 0xE8;
    public final static int UIDStartPos = 1;
}
