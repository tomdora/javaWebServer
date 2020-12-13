all: HTTP3Server.java
	javac -cp . HTTP3Server.java
	
run: HTTP3Server.class
	java -cp . HTTP3Server 3456
	
clean:
	rm -rf HTTP1Server.class serverRun.class
