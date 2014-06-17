package ru.just.justtracker.network;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

public class TCPClient {

	public static final int STATUS_DISCONNECTED = 0;
	public static final int STATUS_CONNECTED = 1;

	private PrintWriter out;
	DataInputStream dataInputStream; 
	private Socket socket;

	public static String SERVER = "188.93.210.50";
	public static int PORT = 10028;

	public TCPClient() {

	}

	public synchronized boolean sendMessage(String message){
		if (out != null && !out.checkError()) {
			byte buffer []= new byte[16];
			String a  = "RCPTOK\r\n";
			try {
				out.println(message);
				out.flush();
				dataInputStream.read(buffer);
				if(new String(buffer).startsWith("RCP")){
					return true;
				}
				return false;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}

	public void closeConnection(){
		try {
			if(out != null)
				out.close();
			if(dataInputStream != null)
				dataInputStream.close();
			if(socket != null)
				socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public static boolean isReachable() {
		return  true;
	}

	public boolean isConnected(){
		if(socket != null && socket.isConnected()){
			boolean connected = socket.isConnected() && ! socket.isClosed();
			return connected;
		}
		return false;
	}

	public void run() throws IOException{
		if(socket == null || !socket.isConnected()){
			InetAddress serverAddr = InetAddress.getByName(SERVER);

			socket = new Socket(serverAddr, PORT);
//			socket.setKeepAlive(true);
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			dataInputStream = new DataInputStream(socket.getInputStream()); 
		}
	}
}
