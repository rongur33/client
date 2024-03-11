package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class ClientTftpProtocol implements MessagingProtocol<byte[]> {

    boolean shouldTerminate;
    Opcode currOpcode;

    String uploadFile;
    LinkedList<byte[]> uploadData;

    byte[] currName;
    LinkedList<byte[]> newFileData;
    String fileToBeCreated;

    public  ClientTftpProtocol(){
        shouldTerminate=false;
    }
    @Override
    public byte[] process(byte[] msg) {
        Opcode myOpcode = peekOpcode(msg);
        byte[] ans;
        if (myOpcode == Opcode.ACK) {
            ans = receiveAck(msg);
        } else if (myOpcode == Opcode.ERROR) {
            ans= error(msg);
        } else if(myOpcode == Opcode.DATA){
            ans =receiveData(msg);
        }else{
            ans=bcast(msg);
        }
        return ans;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    public Opcode peekOpcode(byte[] curr) {
        assert curr.length >= 2;
        int u16Opcode = ((curr[0] & 0xFF) << 8) | (curr[1] & 0xFF);
        return Opcode.fromU16(u16Opcode);
    }

    public byte[] receiveAck(byte[] message) {
        byte[] ans;
        if (currOpcode == Opcode.LOGRQ || currOpcode == Opcode.DELRQ) {
            System.out.println("ACK 0");
            ans=null;
        }
        else if (currOpcode == Opcode.WRQ) {
            byte [] b = {message[2],message[3]};
            short b_short = ( short ) ((( short ) b[0]) << 8 | ( short ) ( b[1]) );
            System.out.println("ACK " + b_short);
            if (message[3] == 0) {
                byte[] dataOfSendingFile=fileToData(uploadFile);
                int chunkSize = 512;
                int arrayLength = dataOfSendingFile.length;
                int numOfChunks = (int) Math.ceil((double) arrayLength / chunkSize);
                uploadData = new LinkedList<byte[]>();
                for (int i = 0; i < numOfChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(arrayLength, start + chunkSize);
                    byte[] chunk = Arrays.copyOfRange(dataOfSendingFile, start, end);
                    uploadData.add(chunk);
                }
            }
            if(uploadData.size()==b_short){
                System.out.println("WRQ "+uploadFile+" complete");
                ans=null;
            }
            else {
                 ans = ackData(b_short, uploadData.get(b_short));
            }
        }
        else {
            System.out.println("ACK 0");
            shouldTerminate=true;
            ans=null;
        }
        return ans;
    }

    private byte[] ackData(short bShort, byte[] bytes) {
        short code=3;
        byte end=0;
        short length=(short)bytes.length;
        byte[] a_bytes = new byte[]{(byte)(length >> 8), (byte)(length & 0xff)};
        byte[] c_bytes= new byte[]{(byte)((bShort+1)>> 8), (byte)((bShort+1) & 0xff)};
        byte[] prependBytes =new byte[]{end,( byte ) (code & 0xff)};
        int combinedLength = prependBytes.length + a_bytes.length + c_bytes.length;
        byte[] combinedArray = new byte[combinedLength];
        System.arraycopy(prependBytes, 0, combinedArray, 0, prependBytes.length);
        System.arraycopy(a_bytes, 0, combinedArray, prependBytes.length, a_bytes.length);
        System.arraycopy(c_bytes, 0, combinedArray, prependBytes.length + a_bytes.length, c_bytes.length);
        byte[] newArray = new byte[combinedArray.length + bytes.length];
        System.arraycopy(combinedArray, 0, newArray, 0, combinedArray.length);
        System.arraycopy(bytes, 0, newArray, combinedArray.length, bytes.length);
        return newArray;
    }


    private byte[] fileToData(String uploadFile) {
        String filePath = "Files\\" + uploadFile;
        try {
            File file = new File(filePath);
            byte[] byteArray = readFileToByteArray(file);
            return byteArray;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] readFileToByteArray(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, bytesRead);
        }

        fis.close();
        bos.close();

        return bos.toByteArray();
    }

    public byte[] error(byte[] msg){
        byte [] b = {msg[2] , msg[3]};
        short errorCode = ( short ) ((( short ) b[0]) << 8 | ( short ) ( b[1]) );
        int startIndex = 4;
        int endIndex = msg.length - 2;
        byte[] subsetArray = Arrays.copyOfRange(msg, startIndex, endIndex);
        String message = new String(subsetArray, StandardCharsets.UTF_8); //לבדוק שעובד
        System.out.println("Error "+errorCode+" : "+message);
        if(currOpcode==Opcode.DISC){
            shouldTerminate=true;
            //close throw main sock.close()-assist function
        }
        if(currOpcode==Opcode.RRQ){
            deleteCreatedFile(fileToBeCreated);
        }
        return null;
    }

    //need to implement
    private void deleteCreatedFile(String fileName) {
        String filePath = "src\\" + fileName;
        try {
            File fileToDelete = new File(filePath);
            fileToDelete.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] bcast(byte[] msg){
        String operation;
        if(msg[2]==1){
            operation="ADDED";
        }else{
            operation="DELETED";
        }
        int startIndex = 3;
        int endIndex = msg.length - 2;
        byte[] subsetArray = Arrays.copyOfRange(msg, startIndex, endIndex);
        String message = new String(subsetArray, StandardCharsets.UTF_8); //לבדוק שעובד
        System.out.println("BCAST: "+" "+operation+" "+message);
        return null;
    }

    public byte[] receiveData(byte[] msg){
        byte[] ans=null;
        if(currOpcode==Opcode.DIRQ){
            int begin=0;
            byte[] data = Arrays.copyOfRange(msg, 6, msg.length);
            for(int i=0;i<data.length;i++){
                if(msg[i]==0){
                    if(currName!=null&&begin==0){
                        byte[] completeName=Arrays.copyOfRange(data, begin, i-1);
                        byte[] combineArray=new byte[completeName.length+currName.length];
                        System.arraycopy(currName, 0, combineArray, 0, currName.length);
                        System.arraycopy(completeName, 0, combineArray, currName.length, completeName.length);
                        String print=new String(combineArray,StandardCharsets.UTF_8);
                        System.out.println(print);
                        begin=i+1;
                        currName=null;
                    }else if(msg[i]==0 &&currName==null) {
                        currName= Arrays.copyOfRange(data, begin, i - 1);
                        String printNew = new String(currName, StandardCharsets.UTF_8);
                        System.out.println(printNew);
                        begin = i + 1;
                        currName=null;
                    }
                } else if(msg[i]!=0 && i==data.length-1){
                    currName=Arrays.copyOfRange(data, begin, i);
                }
            }
            short length= 4;
            byte[] a_bytes = new byte[]{(byte)(length >> 8), (byte)(length & 0xff)};
            ans= new byte[]{a_bytes[0], a_bytes[1], msg[4], msg[5]};
        }
        if(currOpcode==Opcode.RRQ){
            String directoryPath = "src";
            short a=4;
            byte[] separateArray = Arrays.copyOfRange(msg, 6, msg.length);
            newFileData.add(separateArray);
            byte[] ack=new byte []{( byte) (a >> 8) , ( byte ) (a & 0xff)};
            byte[] blockNum= {msg[4],msg[5]};
            ans=new byte[4];
            System.arraycopy(ack, 0, ans, 0, ack.length);
            System.arraycopy(blockNum, 0, ans, ack.length, blockNum.length);
            if(separateArray.length<512){
                try (FileOutputStream fos = new FileOutputStream(Paths.get(directoryPath, uploadFile).toString())) {
                    for(byte[] data:uploadData) {
                        fos.write(data);
                    }
                } catch (IOException e) {e.printStackTrace();}
                String srcFilePath = "src/"+uploadFile; // Change this to the actual path of your file in the src directory
                // Create a File object for the source file
                File srcFile = new File(srcFilePath);

                // Create a File object for the destination directory
                File destDir = new File("Flies");


                // Check if the destination directory exists, if not, create it

                // Create a File object for the destination file
                File destFile = new File(destDir, srcFile.getName());
                srcFile.renameTo(destFile);
            }
        }
        return ans;
    }


}
