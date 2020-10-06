import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.nio.file.*;

// The tutorial can be found just here on the SSaurel's Blog : 
// https://www.ssaurel.com/blog/create-a-simple-http-web-server-in-java
// Each Client Connection will be managed in a dedicated Thread
public class PartialHTTP1Server implements Runnable{ 
	
	static final File WEB_ROOT = new File(".");
	static final String DEFAULT_FILE = "index.html";
	
	BufferedReader inFromClient = null;
	DataOutputStream outToClient = null;
	BufferedOutputStream dataToClient = null;
	
	private Socket connectionSocket = null;
	public PartialHTTP1Server(Socket c) {
		connectionSocket = c;
	}
	
	
	
	@Override
	public void run(){
		try{
			System.out.println("Client " + connectionSocket.getInetAddress() + ":" + connectionSocket.getPort() + " is connected");
			
			connectionSocket.setSoTimeout(5000);
			
			inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			dataToClient = new BufferedOutputStream(connectionSocket.getOutputStream());
			
			String clientInput = inFromClient.readLine();
			String additionalRequest = inFromClient.readLine();
			
			System.out.println("clientInput: " + clientInput);
			
			String[] splitInput = clientInput.split(" ");
			
			String method = splitInput[0];
			String clientRequest = splitInput[1];
			
			String httpVer = null;
			if(splitInput.length == 3){
				httpVer = splitInput[2];
			}
			
			if(method.equals("DELETE") || method.equals("PUT") || method.equals("LINK") || method.equals("UNLINK")){		//check for unimplemented HTTP methods
				System.out.println("501 Not Implemented.\n");
				
				outToClient.writeBytes("HTTP/1.0 501 Not Implemented\r\n");
				outToClient.writeBytes("Date: " + getGMT() + "\r\n");
				outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
				outToClient.writeBytes("\r\n");
				outToClient.flush();
				
				Thread.sleep(250);
				connectionSocket.close();
				return;
				
			} else if(httpVer == null || (!method.equals("GET") && !method.equals("POST") && !method.equals("HEAD"))){		//check if it's then something other than "GET" "POST" or "HEAD"
				System.out.println("400 Bad Request.\n");
				
				outToClient.writeBytes("HTTP/1.0 400 Bad Request\r\n");
				outToClient.writeBytes("Date: " + getGMT() + "\r\n");
				outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
				outToClient.writeBytes("\r\n");
				outToClient.flush();
				
				Thread.sleep(250);
				connectionSocket.close();
				return;
				
			} else if(!httpVer.equals("HTTP/1.0")){															//this project ONLY accepts HTTP 1.0, check for anything else
				System.out.println("Wrong HTTP version. 505 HTTP Version Not Supported.\n");
				
				outToClient.writeBytes("HTTP/1.0 505 HTTP Version Not Supported\r\n");
				outToClient.writeBytes("Date: " + getGMT() + "\r\n");
				outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
				outToClient.writeBytes("\r\n");
				outToClient.flush();
				
				Thread.sleep(250);
				connectionSocket.close();
				return;
				
			} else{
				if(clientRequest.endsWith("/")){
					clientRequest += DEFAULT_FILE;
				}
				
				File requestedFile = new File(WEB_ROOT, clientRequest);
				
				if(!requestedFile.exists()){											//make sure the file exists in the first place
					System.out.println("File does not exist. 404 Not Found.\n");
					
					outToClient.writeBytes("HTTP/1.0 404 Not Found\r\n");
					outToClient.writeBytes("Date: " + getGMT() + "\r\n");
					outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
					outToClient.writeBytes("\r\n");
					outToClient.flush();
					
					Thread.sleep(250);
					connectionSocket.close();
					return;
					
				} else if(!requestedFile.canRead()){									//make sure we actually have access to the file if it does
					System.out.println("File cannot be read. 403 Forbidden.\n");
					
					outToClient.writeBytes("HTTP/1.0 403 Forbidden\r\n");
					outToClient.writeBytes("Date: " + getGMT() + "\r\n");
					outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
					outToClient.writeBytes("\r\n");
					outToClient.flush();
					
					Thread.sleep(250);
					connectionSocket.close();
					return;
					
				} else{																	//then we can start working on the GET, POST, or HEAD
					int fileLength = (int)requestedFile.length();
					
					String contentType = getContentType(clientRequest);
					/*Path requestPath = Paths.get(clientRequest);
					String contentType = Files.probeContentType(requestPath);*/
					
					SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
					sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
					String lastModified = sdf.format(requestedFile.lastModified());
					
					if(method.equals("GET") || method.equals("POST")){					//GET and POST are the same for the project
						System.out.println("Last Modified: " + lastModified + ", Expires: " + getEXP());
						
						byte[] fileData = readFileData(requestedFile, fileLength);
						
						outToClient.writeBytes("HTTP/1.0 200 OK\r\n");
						outToClient.writeBytes("Date: " + getGMT() + "\r\n");
						outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
						outToClient.writeBytes("Allow: GET, POST, HEAD\r\n");
						outToClient.writeBytes("Content-Encoding: identity\r\n");
						outToClient.writeBytes("Content-Length: " + fileLength + "\r\n");
						outToClient.writeBytes("Content-Type: " + contentType + "\r\n");
						outToClient.writeBytes("Expires: " + getEXP() + "\r\n");
						outToClient.writeBytes("Last-Modified: " + lastModified + "\r\n");
						outToClient.writeBytes("\r\n");
						outToClient.flush();
						
						dataToClient.write(fileData, 0, fileLength);
						dataToClient.flush();
						
						System.out.println("File " + clientRequest + " of type " + contentType + " returned\n");
						
						Thread.sleep(250);
						connectionSocket.close();
						return;
						
					} else{																//if not GET or POST it's then HEAD; simply doesn't send data to client
						System.out.println("Last Modified: " + lastModified + ", Expires: " + getEXP());
						
						byte[] fileData = readFileData(requestedFile, fileLength);
						
						outToClient.writeBytes("HTTP/1.0 200 OK\r\n");
						outToClient.writeBytes("Date: " + getGMT() + "\r\n");
						outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
						outToClient.writeBytes("Allow: GET, POST, HEAD\r\n");
						outToClient.writeBytes("Content-Encoding: identity\r\n");
						outToClient.writeBytes("Content-Length: " + fileLength + "\r\n");
						outToClient.writeBytes("Content-Type: " + contentType + "\r\n");
						outToClient.writeBytes("Expires: " + getEXP() + "\r\n");
						outToClient.writeBytes("Last-Modified: " + lastModified + "\r\n");
						outToClient.writeBytes("\r\n");
						outToClient.flush();
						
						System.out.println("File " + clientRequest + " of type " + contentType + " exists\n");
						
						Thread.sleep(250);
						connectionSocket.close();
						return;
						
					}
				}
			}
			
		} catch(FileNotFoundException fnfe){
			System.err.println("File not found: " + fnfe + "\n");
			try{
				outToClient.writeBytes("HTTP/1.0 404 Not Found\r\n");
				outToClient.writeBytes("Date: " + getGMT() + "\r\n");
				outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
				outToClient.writeBytes("\r\n");
				outToClient.flush();
				
				Thread.sleep(250);
				connectionSocket.close();
				return;
				
			} catch(IOException IOErr){
				System.err.println("Server error: " + IOErr + "\n");
			} catch(InterruptedException ie){
				System.err.println("Server error: " + ie);
			}
		} catch(SocketTimeoutException ste){
			System.err.println("Timeout: " + ste);
			
			try{
				outToClient.writeBytes("HTTP/1.0 408 Request Timeout\r\n");
				outToClient.writeBytes("Date: " + getGMT() + "\r\n");
				outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
				outToClient.writeBytes("\r\n");
				outToClient.flush();
				
				Thread.sleep(250);
				connectionSocket.close();
				return;
			} catch(IOException IOErr){
				System.err.println("Server error: " + IOErr + "\n");
			} catch(InterruptedException ie){
				System.err.println("Server error: " + ie);
			}
		} catch(IOException IOErr){
			System.err.println("Server error: " + IOErr);
		} catch(InterruptedException ie){
			System.err.println("Server error: " + ie);
		}
	}
	
	
	
	private String getGMT(){
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		String dateGMT = sdf.format(new Date());
		
		return dateGMT;
	}
	
	private String getEXP(){
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.YEAR, 1);
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		return sdf.format(cal.getTime());
	}
	
	
	
	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		
		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null) 
				fileIn.close();
		}
		
		return fileData;
	}
	
	
	
	private String getContentType(String requestedFile){
		try{
			Path requestPath = Paths.get(requestedFile);
			String contentType = Files.probeContentType(requestPath);
			if(contentType == null){
				return "application/octet-stream";
			} else{
				return contentType;
			}
		} catch(IOException IOErr){
			System.err.println("Server error: " + IOErr);
			return null;
		}
	}



	public static void main(String[] args) throws Exception {
		try {
			int port = Integer.parseInt(args[0]);
			
			ServerSocket serverConnect = new ServerSocket(port);
			System.out.println("Listening for connections on port: " + port + " ...\n");
			
			while (true) {
				PartialHTTP1Server myServer = new PartialHTTP1Server(serverConnect.accept());
				
				System.out.println("Connecton opened. (" + new Date() + ")");
				
				Thread serverThread = new Thread(myServer);
				serverThread.start();
			}
			
		} catch (IOException serverError) {
			System.err.println("Server Connection error : " + serverError.getMessage());
		}
	}
}
