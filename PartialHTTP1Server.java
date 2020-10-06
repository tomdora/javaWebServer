//Main code for the server, uses serverRun for each thread
//Thomas Fiorilla, trf40

import java.io.*;
import java.net.*;
import java.util.*;

public class PartialHTTP1Server{ 
	public static void main(String[] args) throws Exception {
		try {
			int port = Integer.parseInt(args[0]);
			
			ServerSocket serverConnect = new ServerSocket(port);
			System.out.println("Listening for connections on port: " + port + " ...\n");
			
			while (true) {
				serverRun myServer = new serverRun(serverConnect.accept());
				
				System.out.println("Connecton opened. (" + new Date() + ")");
				
				Thread serverThread = new Thread(myServer);
				serverThread.start();
			}
			
		} catch (IOException serverError) {
			System.err.println("Server Connection error : " + serverError.getMessage());
		}
	}
}
