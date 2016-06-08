public class fcntcp {

	public static void main(String[] args) {
		
		try{
		
				//Check for the length - atleast two args should be specified
				if(args.length >= 2){
					
					UDPHost objHost;
					
					//Extract the port number
					int portOfHost = Integer.parseInt(args[args.length-1]);
					System.out.println("Port number of the host: "+ portOfHost);
					
					//Check for diagnostic flag
					boolean diagFlag = extractDiagnosticFlag(args);
					System.out.println("Diagnostic flag is set to: "+ diagFlag);
					
					//Check for the presence of the -c / --client or -s/ --server
					if(checkForStringInArr(args, "-c") || checkForStringInArr(args, "--client")){
						
						System.out.println("Host will be started as a client.");
						
						//Extract ip from CommandLine
						String ipOfServer = args[args.length -2];
						System.out.println("IP of server: "+ ipOfServer);
						
						//Extract the fully qualified file name
						String fileName = extractFileName(args);
						System.out.println("Fully qualified file name: "+ fileName);
						
						//Extract the timeout, if specified by the client
						long initTimeOut = extractTimeout(args);
						System.out.println("Timeout of client "+ initTimeOut);
						
						//Initialize Client with UDP port, ip of server, file name timeout, detailed diagnostics
						objHost = new UDPClient(portOfHost, ipOfServer,  diagFlag, fileName, initTimeOut);
						
					}
					//Default initialization of host to Server mode
					else{
						
						System.out.println("Host will be started as a server.");
						
						//Initialize Server
						objHost = new UDPServer(diagFlag, portOfHost);
						
						System.out.println("Server initialized.");
					}
					
					//Start the host
					objHost.begin();
					
				}
				else{
					System.out.println("Usage is as follows");
					System.out.println("fcntcp -{c,s} [options] [server address] port \n"
							+ "-c, --client : run as client	\n"
							+ "-s, --server : run as server \n"
							+ "-f <file>, --file: specify file for client to send. client applications \n"
							+ "-t <#>, --timeout: timeout in milliseconds for retransmit timer \n"
							+ "-q, --quiet: do not output detailed diagnostics. client & server applications \n"
							+ "server address : address of server to connect to. client applications \n"
							+ "port : primary connection port client & server applications"
							);
				}

		}
		catch(Exception e){
			
			
			//Print out the error
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
					

	}
	
	
	
	
	
	//Function to set the default time out value
	public static int extractTimeout(String [] args){
		
		int returnVal = 1000; //Default return value
		
		//Check all arguments till the last two arguments
		for(int i=0; i< args.length - 2; ++i ){
			
			if(args[i].equalsIgnoreCase("-t") || args[i].equalsIgnoreCase("--timeout"))
					//if file option is present then return the following argument
					return Integer.parseInt(args[i+1]);
		}
		
		return returnVal;
	}
	
	
	
	//Function to extract the file name from command line arguments
	//If file name is not present then function throws and exception 
	// as a client is supposed to have a file to send
	public static String extractFileName(String [] args) throws Exception{
		
		//Check all arguments till the last two arguments
		for(int i=0; i< args.length - 2; ++i ){
			
			if(args[i].equalsIgnoreCase("-f") || args[i].equalsIgnoreCase("--file")){
				
					//if file option is present then return the following argument
					return args[i+1];
			}
		}
		
		throw new Exception("File name is manadatory for the client.");
		
	}
	
	
	
	
	
	//Function to extract the diagnostic flag
	public static boolean extractDiagnosticFlag(String [] args){
		
		//Check all check all but the last command line argument
		//The last argument is expected to be the port number of 
		//the host
		for(int i=0; i<args.length-1; ++i){
			if(args[i].equalsIgnoreCase("-q") || args[i].equalsIgnoreCase("-quiet"))
				return true;
		}
		
		return false;
	}
	
	
	
	
	
	//Function to check for the existence of a string in a string array
	public static boolean checkForStringInArr(String [] arrStr, String str){
		 
		boolean retFlag =  false;
		
		for(String strFrmArr :  arrStr){
			
			if(strFrmArr.trim().toLowerCase().contains(str.trim().toLowerCase())){
				retFlag = true;
				break;
			}
		}
		return retFlag;
		
	}

}
