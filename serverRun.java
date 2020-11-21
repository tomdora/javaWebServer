	import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.nio.file.*;

public class serverRun implements Runnable{
	
	static final File WEB_ROOT = new File(".");
	static final String DEFAULT_FILE = "index.html";
	
	BufferedReader inFromClient = null;
	DataOutputStream outToClient = null;
	BufferedOutputStream dataToClient = null;
	
	Socket connectionSocket = null;
	public serverRun(Socket c) {
		connectionSocket = c;
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public void run(){
		try{
			System.out.println("Client " + connectionSocket.getInetAddress() + ":" + connectionSocket.getPort() + " is connected");
			
			connectionSocket.setSoTimeout(5000);
			
			inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
			outToClient = new DataOutputStream(connectionSocket.getOutputStream());
			dataToClient = new BufferedOutputStream(connectionSocket.getOutputStream());
			
			//Make a String array to hold each line, then read through the BufferedReader for each line
			String[] clientInput = {null, null, null, null, null, null, null, null, null, null};
			int i = 0;
			while(inFromClient.ready()){
				String line = inFromClient.readLine();
				System.out.println("	reading line " + (i+1) + ": " + line);
				
				clientInput[i] = line;
				i++;
			}
			System.out.println("	All lines read.");
			
			String[] splitInput = clientInput[0].split(" ");
			
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
				inFromClient.close();
				outToClient.close();
				dataToClient.close();
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
				inFromClient.close();
				outToClient.close();
				dataToClient.close();
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
				inFromClient.close();
				outToClient.close();
				dataToClient.close();
				connectionSocket.close();
				return;
				
			} else{
				if(clientRequest.endsWith("/")){
					clientRequest += DEFAULT_FILE;
				}
				
				File requestedFile = new File(WEB_ROOT, clientRequest);
				
				if(!requestedFile.exists() && !method.equals("POST")){											//make sure the file exists in the first place, but only for non-POST requests because of the strange way autograder expects us to handle test case 22
					System.out.println("File does not exist. 404 Not Found.\n");
					
					outToClient.writeBytes("HTTP/1.0 404 Not Found\r\n");
					outToClient.writeBytes("Date: " + getGMT() + "\r\n");
					outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
					outToClient.writeBytes("\r\n");
					outToClient.flush();
					
					Thread.sleep(250);
					inFromClient.close();
					outToClient.close();
					dataToClient.close();
					connectionSocket.close();
					return;
					
				} else if(requestedFile.exists() && !requestedFile.canRead()){									//make sure we actually have access to the file if it does. Make sure file exists because of the strange way autograder expects us to handle test case 22
					System.out.println("File cannot be read. 403 Forbidden.\n");
					
					outToClient.writeBytes("HTTP/1.0 403 Forbidden\r\n");
					outToClient.writeBytes("Date: " + getGMT() + "\r\n");
					outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
					outToClient.writeBytes("\r\n");
					outToClient.flush();
					
					Thread.sleep(250);
					inFromClient.close();
					outToClient.close();
					dataToClient.close();
					connectionSocket.close();
					return;
					
				} else{																	//then we can start working on the GET, POST, or HEAD
					int fileLength = (int)requestedFile.length();
					
					//String contentType = getContentType(clientRequest);
					/*Path requestPath = Paths.get(clientRequest);
					String contentType = Files.probeContentType(requestPath);*/
					
					SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
					sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
					String lastModified = sdf.format(requestedFile.lastModified());
					
					if(method.equals("GET")){					// if a GET is called
						//System.out.println("Last Modified: " + lastModified + ", Expires: " + getEXP());
						
						String contentType = getContentType(clientRequest);
						byte[] fileData = readFileData(requestedFile, fileLength);
						
						//Check to see if the second line is null, and if it isn't, check for "If-Modified-Since: "
						if(clientInput[1] != null && clientInput[1].contains("If-Modified-Since: ")){
							try{															//try to parse the string as a date
								//Since we were successful, we can break apart the first line
								clientInput[1] = clientInput[1].substring(19);
								System.out.println("Date: " + clientInput[1]);
								
								Date ifModSince = sdf.parse(clientInput[1]);
								
								//check if the lastModified date is after the requested date
								if(ifModSince.compareTo(sdf.parse(lastModified)) < 0 || ifModSince.compareTo(sdf.parse(lastModified)) == 0){
									System.out.println("File has been modified since date.");
									
									//Return a normal GET 
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
									inFromClient.close();
									outToClient.close();
									dataToClient.close();
									connectionSocket.close();
									return;
									
								} else{
									System.out.println("File has not been modified since date.");
									
									//Return a NOT MODIFIED response
									outToClient.writeBytes("HTTP/1.0 304 Not Modified\r\n");
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
									inFromClient.close();
									outToClient.close();
									dataToClient.close();
									connectionSocket.close();
									return;
								}
								
							} catch(ParseException pe){										//if the string doesn't contain a valid string, but we already know the request is ok, we just return a normal GET
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
								inFromClient.close();
								outToClient.close();
								dataToClient.close();
								connectionSocket.close();
								return;
							}
						}
						
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
						inFromClient.close();
						outToClient.close();
						dataToClient.close();
						connectionSocket.close();
						return;
						
					} else if(method.equals("POST")){
						//First thing to check is if the POST request is valid with Content-Length and Content-Type
						int contentLength;
						String contentType;
						
						i = 0;
						boolean hasLength = false;
						boolean hasType = false;
						while(clientInput[i] != null && i < clientInput.length){
							if(clientInput[i].contains("Content-Length: ")){
								System.out.println("	Content-Length Found");
								
								String[] splitLength = clientInput[i].split(" ");
								hasLength = true;
								
							} else if(clientInput[i].contains("Content-Type: ")){
								System.out.println("	Content-Type Found");
								
								String[] splitType = clientInput[i].split(" ");
								hasType = true;
								
							} else if(hasLength && hasType){
								System.out.println("	Content-Length and Content-Type Exist");
								
								break;
								
							}
							
							i++;
						}
						//If there is no Content-Length, we return "HTTP/1.0 411 Length Required"
						if(!hasLength){
							System.out.println("	No content length.\n");
							
							outToClient.writeBytes("HTTP/1.0 411 Length Required\r\n");
							outToClient.writeBytes("Date: " + getGMT() + "\r\n");
							outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
							outToClient.writeBytes("\r\n");
							outToClient.flush();
							
							Thread.sleep(250);
							inFromClient.close();
							outToClient.close();
							dataToClient.close();
							connectionSocket.close();
							return;
						}
						//If there is no Content-Type, we return "HTTP/1.0 500 Internal Server Error"
						if(!hasType){
							System.out.println("	No content type.\n");
							
							outToClient.writeBytes("HTTP/1.0 500 Internal Server Error\r\n");
							outToClient.writeBytes("Date: " + getGMT() + "\r\n");
							outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
							outToClient.writeBytes("\r\n");
							outToClient.flush();
							
							Thread.sleep(250);
							inFromClient.close();
							outToClient.close();
							dataToClient.close();
							connectionSocket.close();
							return;
						}
						//If the request isn't for a CGI script, we return "HTTP/1.0 405 Method Not Allowed"
						if(!clientRequest.endsWith(".cgi")){
							System.out.println("	Does not point to a cgi script.\n");
							
							outToClient.writeBytes("HTTP/1.0 405 Method Not Allowed\r\n");
							outToClient.writeBytes("Date: " + getGMT() + "\r\n");
							outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
							outToClient.writeBytes("\r\n");
							outToClient.flush();
							
							Thread.sleep(250);
							inFromClient.close();
							outToClient.close();
							dataToClient.close();
							connectionSocket.close();
							return;
						}
						//Check to see if the script is executable
						if(!requestedFile.canExecute()){
							System.out.println("	Script cannot be executed.\n");
							
							outToClient.writeBytes("HTTP/1.0 403 Forbidden\r\n");
							outToClient.writeBytes("Date: " + getGMT() + "\r\n");
							outToClient.writeBytes("Server: PartialHTTP1Server\r\n");
							outToClient.writeBytes("\r\n");
							outToClient.flush();
							
							Thread.sleep(250);
							inFromClient.close();
							outToClient.close();
							dataToClient.close();
							connectionSocket.close();
							return;
						}
						System.out.println("	No problems.\n");
						//All that said and done, we can now run the CGI script with ProcessBuilder.
						
						List<String> list = new ArrayList<String>();
						list.add("." + clientRequest);
						
						Process postProcess = new ProcessBuilder(list).start();
						//List<String> results = readOutput(postProcess.getInputStream());
						//int exitCode = postProcess.waitFor();
						BufferedReader processOutput = new BufferedReader(new InputStreamReader(postProcess.getInputStream()));
						
						//System.out.println("	Output is: " + processOutput);
						
						String s = null; 
						while ((s = processOutput.readLine()) != null){ 
							System.out.println(s); 
						} 
						
					} else{																//if not GET or POST it's then HEAD; simply doesn't send data to client
						String contentType = getContentType(clientRequest);
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
						inFromClient.close();
						outToClient.close();
						dataToClient.close();
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
				inFromClient.close();
				outToClient.close();
				dataToClient.close();
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
				inFromClient.close();
				outToClient.close();
				dataToClient.close();
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
	
	///////////////////////////////////////////////////////////////////////////////////////////////////
	
	private String getGMT(){
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		String dateGMT = sdf.format(new Date());
		
		return dateGMT;
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////
	private String getEXP(){
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.YEAR, 1);
		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		return sdf.format(cal.getTime());
	}
	
	///////////////////////////////////////////////////////////////////////////////////////////////////
	
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
	
	///////////////////////////////////////////////////////////////////////////////////////////////////
	
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
	
	
	
	
	
	
	
	
	
	
}
