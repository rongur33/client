package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Scanner;

public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) {
        Thread keyboard;
        Thread listening;
        try (Socket sock = new Socket("localhost", 7777);
             BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());){
            ClientTftpProtocol protocol=new ClientTftpProtocol();
            TftpClientEncoderDecoder encDec=new TftpClientEncoderDecoder();


            keyboard=new Thread(() -> {
                while(!protocol.shouldTerminate()){
                    Scanner scanner=new Scanner(System.in);
                    String str=scanner.nextLine();
                    byte[] send= keyboardProtocol(str,protocol);
                    if(send!=null){
                        try {
                            out.write(encDec.encode(send));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        try {
                            out.flush();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            },"keyboard");

            listening=new Thread(()->{
                int read;
                while (true) {
                    try {
                        if (!(!protocol.shouldTerminate()  && (read = in.read()) >= 0)) break;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    byte[] nextMessage = encDec.decodeNextByte((byte) read);
                    if (nextMessage != null) {
                        byte[] toProtocol= protocol.process(nextMessage);
                        if(toProtocol!=null){
                            try {
                                out.write(encDec.encode(toProtocol));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            try {
                                out.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                }
            },"listening");
            listening.start();
            keyboard.start();


        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }



    }

    private static byte[] keyboardProtocol(String str,ClientTftpProtocol protocol) {
        short opcode;
        byte end=0;
        byte[] ans;
        String userName;
        String substring3=str.substring(0,4);
        String substring6=str.substring(0,6);
        if(substring3.equals("RRQ ")){
            protocol.currOpcode=Opcode.RRQ;
            opcode=1;
            userName=str.substring(4);
            if(isFileExistsWithMatchingName(userName,"Files")){
                System.out.println("file already exist");
                ans=null;
            }
            else{
                protocol.fileToBeCreated=userName;
                protocol.newFileData=new LinkedList<byte[]>();
                String directoryPath = "src";
                try {
                    // Create directory if it doesn't exist
                    Files.createDirectories(Paths.get(directoryPath));

                    // Create the file
                    Path filePath = Paths.get(directoryPath, userName);
                    Files.createFile(filePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                byte[] userNameBytes=userName.getBytes(StandardCharsets.UTF_8);
                byte[] a_bytes = new byte[]{(byte)(opcode >> 8), (byte)(opcode & 0xff)};
                ans=new byte[userNameBytes.length+a_bytes.length+1];
                System.arraycopy(a_bytes, 0, ans, 0, a_bytes.length);
                System.arraycopy(userNameBytes, 0, ans, a_bytes.length, userNameBytes.length);
                ans[ans.length-1]=end;
            }
        } else if (substring3.equals("WRQ ")) {
            protocol.currOpcode=Opcode.WRQ;
            userName=str.substring(4);
            if(!isFileExistsWithMatchingName(userName,"Files")){
                System.out.println("file does not exist");
                ans=null;
            }
            else {
                protocol.uploadFile=userName;
                opcode=2;
                byte[] userNameBytes=userName.getBytes(StandardCharsets.UTF_8);
                byte[] a_bytes = new byte[]{(byte)(opcode >> 8), (byte)(opcode & 0xff)};
                ans=new byte[userNameBytes.length+a_bytes.length+1];
                System.arraycopy(a_bytes, 0, ans, 0, a_bytes.length);
                System.arraycopy(userNameBytes, 0, ans, a_bytes.length, userNameBytes.length);
                ans[ans.length-1]=end;
            }
        }
        else if(substring6.equals("LOGRQ ")){
            protocol.currOpcode=Opcode.LOGRQ;
            opcode=7;
            userName=str.substring(6);
            byte[] userNameBytes=userName.getBytes(StandardCharsets.UTF_8);
            byte[] a_bytes = new byte[]{(byte)(opcode >> 8), (byte)(opcode & 0xff)};
            ans=new byte[userNameBytes.length+a_bytes.length+1];
            System.arraycopy(a_bytes, 0, ans, 0, a_bytes.length);
            System.arraycopy(userNameBytes, 0, ans, a_bytes.length, userNameBytes.length);
            ans[ans.length-1]=end;
        } else if (substring6.equals("DELRQ ")) {
            protocol.currOpcode=Opcode.DELRQ;
            opcode=8;
            userName=str.substring(6);
            byte[] userNameBytes=userName.getBytes(StandardCharsets.UTF_8);
            byte[] a_bytes = new byte[]{(byte)(opcode >> 8), (byte)(opcode & 0xff)};
            ans=new byte[userNameBytes.length+a_bytes.length+1];
            System.arraycopy(a_bytes, 0, ans, 0, a_bytes.length);
            System.arraycopy(userNameBytes, 0, ans, a_bytes.length, userNameBytes.length);
            ans[ans.length-1]=end;
        } else if (substring3.equals("DIRQ")) {
            protocol.currOpcode=Opcode.DIRQ;
            opcode=6;
            ans = new byte[]{(byte)(opcode >> 8), (byte)(opcode & 0xff)};
        }else if (substring3.equals("DIRQ")) {
            protocol.currOpcode=Opcode.DISC;
            opcode=10;
            ans = new byte[]{(byte)(opcode >> 8), (byte)(opcode & 0xff)};
        }
        else {
            System.out.println("Invalid command!");
            ans=null;
        }
        return ans;
    }

    public static boolean isFileExistsWithMatchingName(String inputString, String directoryPath) {
        // Create a File object for the directory
        File directory = new File(directoryPath);

        // Check if the directory exists
        if (directory.exists() && directory.isDirectory()) {
            // Get list of files in the directory
            File[] files = directory.listFiles();

            // Check if any file name matches the input string
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().equals(inputString)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void close() throws IOException {

    }
}