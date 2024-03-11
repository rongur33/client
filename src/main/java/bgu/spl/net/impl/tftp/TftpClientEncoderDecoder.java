package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
enum Opcode {
    None, RRQ, WRQ, DATA, ACK, ERROR, DIRQ, LOGRQ, DELRQ, BCAST, DISC;

    public static Opcode fromU16(int opcode) {
        switch (opcode) {
            case 1:
                return RRQ;
            case 2:
                return WRQ;
            case 3:
                return DATA;
            case 4:
                return ACK;
            case 5:
                return ERROR;
            case 6:
                return DIRQ;
            case 7:
                return LOGRQ;
            case 8:
                return DELRQ;
            case 9:
                return BCAST;
            case 10:
                return DISC;
            default:
                return None;
        }
    }
}
public class TftpClientEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    //private Byte[] bytes;
    private List<Byte> bytes=new ArrayList<>();
    private Opcode opcode=Opcode.None;
    private int optExpectedLen=Integer.MAX_VALUE;;

    //TODO: Implement here the TFTP encoder and decoder


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        byte[] ans;
        if (bytes.size() >= optExpectedLen && nextByte == 0x0) {
            //  Opcode opcode = getOpcode();
            List<Byte> mes = poolBytes();
            setOpcode(Opcode.None);
            ans=new byte[mes.size()];
            for(int i=0;i<ans.length;i++){
                ans[i]= mes.get(i);
            }
            return ans;
        }else {
            bytes.add(nextByte);
            if (bytes.size() == 2) {
                setOpcode(peekOpcode());
            }
            if (opcode == Opcode.DATA && bytes.size() == 4) {
                int size = ((bytes.get(2) & 0xFF) << 8) | (bytes.get(3) & 0xFF);
                optExpectedLen = 6 + size;
            }
            if (!haveAddedZero(opcode) && bytes.size() == optExpectedLen) {
                //    Opcode opcode = getOpcode();
                List<Byte> mes = poolBytes();
                setOpcode(Opcode.None);
                ans = new byte[mes.size()];
                for (int i = 0; i < ans.length; i++) {
                    ans[i] = mes.get(i);
                }
                return ans;
            }
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    private void setOpcode(Opcode opcode) {
        this.opcode = opcode;
        optExpectedLen = switch (opcode) {
            case None -> Integer.MAX_VALUE;
            case RRQ, WRQ, DIRQ, LOGRQ, DELRQ, DISC -> 2;
            case BCAST -> 3;
            case ACK, ERROR -> 4;
            case DATA -> 6;
        };
    }

    private Opcode getOpcode() {
        return opcode;
    }

    private boolean haveAddedZero(Opcode opcode) {
        return switch (opcode) {
            case RRQ, WRQ, ERROR, BCAST, LOGRQ, DELRQ, None -> true;
            default -> false;
        };
    }
    public List<Byte> poolBytes() {
        List<Byte> mes = new ArrayList<>(bytes);
        bytes.clear();
        setOpcode(Opcode.None);
        return mes;
    }

    private Opcode peekOpcode() {
        assert bytes.size() >= 2;
        int u16Opcode = ((bytes.get(0) & 0xFF) << 8) | (bytes.get(1) & 0xFF);
        return Opcode.fromU16(u16Opcode);
    }

}