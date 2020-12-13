//Main code for the server, uses serverRun for each thread
//Thomas Fiorilla, trf40

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class HTTP3Server{ 
	public static void main(String[] args) throws Exception {
		try {
			ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 50, 1, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
			
			int port = Integer.parseInt(args[0]);
			
			ServerSocket serverConnect = new ServerSocket(port);
			System.out.println("Listening for connections on port: " + port + " ...\n");
			
			while (true) {
				System.out.println("Connecton opened. (" + new Date() + ")");
				
				executor.execute(new serverRun(serverConnect.accept()));
			}
			
			
			
		} catch (IOException serverError) {
			System.err.println("Server Connection error : " + serverError.getMessage());
		}
	}
}
