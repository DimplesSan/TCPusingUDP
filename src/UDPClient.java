import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import javax.lang.model.element.PackageElement;

public class UDPClient extends UDPHost {

	private String ipAddress;
	private int portOfServer;
	private String absFileName;
	private long timeOut;
	private long startTime; //timer for start of file
	private long sessionStartTime; //Timer for session start time
	private long endTime, RTT; //timer for end of file and RTT
	private TreeMap<Integer, Long> packetRTTs;
	
	private int numOfReTransmissions;
	
	private Checksum chkSum; //Checksum
	private MessageDigest messageDigest;
	private byte[] hashValue; 
	
	private TreeMap<Integer, DatagramPacket> sentPackets;
	private TreeSet<Integer> ackPackets;
	private int servRCWND;
	private int cwnd;
	private TreeSet<Integer> cwndSeqNums;
	
	//Constructor to initialize the field values
	public UDPClient(int _portOfHost, String _ipOfServer,  boolean _diagFlag, 
					 String _fileName, long _initTimeOut) throws SocketException, NoSuchAlgorithmException {
		
		this.udpPort = _portOfHost;
		this.portOfServer = _portOfHost;
		this.ipAddress = _ipOfServer;
		this.diagnosticFlag = _diagFlag;
		this.absFileName = _fileName;
		this.timeOut = _initTimeOut;
		this.numOfReTransmissions = 5;
		this.servRCWND = 1;
		this.cwnd = 1;
		
		chkSum = new CRC32();
		messageDigest = MessageDigest.getInstance("SHA1"); //Hashing Function
		
		hostUDPSocket = new DatagramSocket(this.udpPort);
		
		//Set the time out to default one sec
		hostUDPSocket.setSoTimeout((int)this.timeOut);
		
		sentPackets = new TreeMap<Integer, DatagramPacket>();
		packetRTTs = new TreeMap<Integer, Long>();
		ackPackets = new TreeSet<Integer>();
		
		System.out.println("Client initialized.");
	}

	
	
	
	
	@Override
	public void begin() throws Exception {
		
		System.out.println("Trying to establish connection with server");
		
		//Establish Connection
		if(establishConn()){
			
			System.out.println("Connection established with the sever at IP: "
					                + this.ipAddress +
					                " and port: "+ this.portOfServer
								   );
			
			//transfer the file
			transferFile();
				
			//Send Hash
			sendHashValue();
			
			//Tear down the Connection
			tearDownConnection();
			
			System.out.println("Client exiting");
		}
		else
			throw new Exception("Connection could not be established with host "
								+"with ip address: "+this.ipAddress
								+" & port: "+ this.portOfServer);
	}
	
	
	
	
	private void tearDownConnection() throws IOException{
		
		System.out.println("Closing the connection");
		
		//build close message
		this.packageMsg(this.buildMsg(40, 4, (long)0, (long)0, (long)0, 0, 0, null));
		
		//send the close message
		this.hostUDPSocket.send(this.hostUDPSendPacket);
		
		//wait for acknowlegement of the close message
		this.printDiagnosticMsg("Waiting for acknowledgement for close message ");
		waitForRetransmission();
		
		
	}
	
	
	
	//Function to send the computed hash value
	private void sendHashValue() throws IOException{
		
		StringBuffer strValOfHashInHex = new StringBuffer();
		for(int i = 0; i< this.hashValue.length; ++i)
			strValOfHashInHex.append(Integer.toString((this.hashValue[i] & 0xff) + 0x100, 16).substring(1));
		
		System.out.println("Hash value in Hex for the file transferred is: " + strValOfHashInHex.toString());
		
		packageMsg(buildMsg(40, 3, (long)0, (long)0, calcCheckSum(this.hashValue), 0, this.hashValue.length, this.hashValue));
		this.hostUDPSocket.send(this.hostUDPSendPacket); //Send the hash value
		
		this.hostUDPRecPacket = new DatagramPacket(new byte[40 + this.hashValue.length], this.hashValue.length + 40);
		this.hostUDPSocket.setSoTimeout((int)this.timeOut);//set time out
		
		//Wait for ack from server
		while(true){
			
			try{
				
				this.hostUDPSocket.receive(this.hostUDPRecPacket);
				break;
			}catch(SocketTimeoutException se){
				
				this.printDiagnosticMsg("Timed out while waiting for ack for the hash value. Resending the hash value");
				this.hostUDPSocket.send(this.hostUDPSendPacket); //Send the hash value again		
			}
			
		}
		
		//Acknowledgement received
		int ackN = (int)ByteBuffer.wrap(this.hostUDPRecPacket.getData(), 16 , 8).getLong();
		String msgFrmServer = "Hash code match failed.";
		if(ackN == 1)
			msgFrmServer = "Hash codes matched successfully. ";
		
		System.out.println("Ack for hash value received. Message from sever: "+msgFrmServer );
		
	}
	
	
	
	
	//Transfer the file from client to server
	private void transferFile() throws IOException{
		
		System.out.println("File transfer initiated.");
		
		File objFile =  new File(this.absFileName);
		double fileSZ = objFile.length();
		double modFileSz = fileSZ % 1000;
		double numOfPackets = 0;
		double loopEndCounter;
		double lstPktSize;
		//Calc the number of packets to be sent across
		if(modFileSz == 0.0){
			numOfPackets = fileSZ /1000;
			loopEndCounter = numOfPackets - 1;
			lstPktSize = 1000;
		}
		else{
			numOfPackets = (fileSZ /1000) + 1; //1 extra packet is required to be transmitted
			loopEndCounter = numOfPackets - 2;
			lstPktSize = modFileSz;
		}
		
		BufferedInputStream objBIS = new BufferedInputStream(new FileInputStream(objFile));
		
		byte [] tempBuff = new byte[1000]; //Temp buffer to hold data
		
		this.cwnd = 1; //Set the size of the command window
		this.cwndSeqNums = new TreeSet<Integer>();
		int msgType = 1; //1 --> File transfer
		
		//Repeat till second last packet 
		for(int i = 0; i< loopEndCounter ;){
			
			//Repeat till size of cwnd
			for(int j =0; j< this.cwnd && i< loopEndCounter; ++j, ++i){
				
				
				
				objBIS.read(tempBuff); //Read byte stream
				
				long calcCheckSum = calcCheckSum(tempBuff); //Calc Check suum
				
				messageDigest.update(tempBuff); //Generate Hash value
				
				packageMsg(buildMsg(40, msgType, (long)i, (long)0, calcCheckSum, 0, tempBuff.length, tempBuff)); // build and package data
				
				sentPackets.put(i, this.hostUDPSendPacket); //Add the packet to list of sent files
				cwndSeqNums.add(i); // Add packet to the list of packets waiting for acknowledgements

//				this.packetRTTs.put(i, System.currentTimeMillis());//Note the time for the packet before sending
				
				hostUDPSocket.send(this.hostUDPSendPacket);//Send it
				this.printDiagnosticMsg("Packet sent. Seq num: " + i);
			}

			
			//Repeat till all pakets cwnd list are acked
			while(!cwndSeqNums.isEmpty()){
				//Wait for ack -
				waitForAck(this.cwnd, this.cwndSeqNums);
			}
		}
		
	    //for last packet
	    byte [] tempBuffLst = new byte[(int)lstPktSize]; //Re-Define buffer size to remaining portion
	    byte r;
	    int i = 0;
	    while((r=(byte)objBIS.read())!= -1){
		    tempBuffLst[i] = r;
		    ++i;
	    }
	    
	    this.cwnd = 1; //re set the cwnd to a single packet
	    msgType = 2; //Set the msg type to end of file
	
	    long calcCheckSum = calcCheckSum(tempBuffLst); //Calc Check suum
	    messageDigest.update(tempBuffLst); //Generate Hash value
	    packageMsg(buildMsg(40, msgType, (long)(numOfPackets-1), (long)0, calcCheckSum, 0, tempBuffLst.length, tempBuffLst)); // build and package data
	    sentPackets.put((int)(numOfPackets-1), this.hostUDPSendPacket); //Add the packet to list of sent files
	    cwndSeqNums.add((int)(numOfPackets-1)); // Add packet to the list of packets waiting for acknowledgements
	    hostUDPSocket.send(this.hostUDPSendPacket);//Send it
	    this.printDiagnosticMsg("Packet sent. Seq num: " + (int)(numOfPackets -1) );
	    waitForAck(this.cwnd, this.cwndSeqNums);


		
		System.out.println("File transfer completed");
		
		//Complete the computation for the hash value 
		this.hashValue = messageDigest.digest();
		
	}
	
	
	
	//Function that retransmits till all packet in the command window are
	//acknowledged
	private void waitForAck(int cwndCnt, TreeSet<Integer> cwndList) throws IOException{
		
		this.hostUDPRecPacket = new DatagramPacket(new byte[140], 140);
		
		while(true){
			
			try{
				
				//break out of loop
				hostUDPSocket.receive(hostUDPRecPacket);
				
				this.RTT = System.currentTimeMillis() - this.startTime; //Update current RTT
				
				byte[] dataFromSev = hostUDPRecPacket.getData();
				long ackN = ByteBuffer.wrap(dataFromSev, 16, 8 ).getLong();
				
//				System.out.println("ackN: " + (int)ackN);
//				for(int i : this.packetRTTs.keySet())
//					System.out.println("From RTT of packets i: " + i );
//				long packRTTTemp =  System.currentTimeMillis() - this.packetRTTs.get((int)ackN);
//				this.packetRTTs.put((int)ackN, packRTTTemp); 
				
				int currRCWND = ByteBuffer.wrap(dataFromSev, 32, 4 ).getInt(); 
						
				//Check the list of packets in the cwnd
				//if ack of rec pack present
				if(checkValInSet((int)ackN, cwndList)){
					
					this.printDiagnosticMsg("Ack received for seq num: " + ackN);// + " Current RTT: "+ this.packetRTTs.get((int)ackN));
					
					this.ackPackets.add((int)ackN); //Add to seq num of ack packet acked Map
					
					cwndList.remove((int)ackN); //Remove seq num pack from waiting list
				
					if(this.cwnd < 10){
						
						int dblCwnd = cwndCnt * 2; //default threshold is 32
						if(dblCwnd * 2 < 32){
							
							//double the cwnd
							cwndCnt = dblCwnd;
							this.cwnd = cwndCnt;
							this.printDiagnosticMsg("cwnd doubled. cwnd: "+cwndCnt );
						}
						else{
							// else --> increase linearly
//							this.cwnd = currRCWND;
							this.cwnd ++;
							this.printDiagnosticMsg("cwnd incremented by 1. cwnd: "+this.cwnd );
						}
					}
					else{
						
						this.cwnd = 10;
					}


					
					break;
				}	

				
			}
			catch(SocketTimeoutException e){  //if timeout
				
				int oldCWND = this.cwnd;

				//Loss Detected
				//Half the cwnd
				if(this.cwnd /2 >0 )
					this.cwnd = this.cwnd/2;
				else
					this.cwnd = 1;
				
				this.printDiagnosticMsg("Loss detected. Old value: "+ oldCWND +" New cwnd : " + this.cwnd);
				
				this.startTime = System.currentTimeMillis();
				
				//Retransmit all cwnd packet
				for(int i : cwndList){
					
					printDiagnosticMsg("Retransmitting the packet with seq  num : "+ i);
//					packetRTTs.put(i, System.currentTimeMillis());//Reenter times 
					hostUDPSocket.send(this.sentPackets.get(i));
				}
				
			}
			
		}
		
	}
	
	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	
	
	
	
	
	
	//Internal function to check for the existence of a key
	public boolean checkKeyInMap(int val){
		
		return checkValInSet(val, sentPackets.keySet());
	}
	
	

	

	
	
	//Function to calculate the CRC checksum of the supplied data
	private long calcCheckSum(byte [] data){
		
		this.chkSum = new CRC32();
		
		//Calculate the CRC checksum of the data
		this.chkSum.update(data, 0, data.length);
		
		//Return the longvalue
		return this.chkSum.getValue();
	}
	
	
	
	
	
	
	
	
	
	
	
	//Function to return true if connection was
	//successfully established else false
	//3 way hand shake 
	public boolean establishConn() throws Exception{
		
		boolean retVal = false;
		
		//Build init message
		byte [] temp = this.buildMsg(40, 0, (long)1, 
				                    (long)0, (long)0, 
				                    0, 0, null);
		packageMsg(temp);

		
		try {
			
			this.printDiagnosticMsg("Sending init message to server.");

			//Send InitMessage			
			hostUDPSocket.send(this.hostUDPSendPacket);
			
			//Note the start time after sending the message 
			this.sessionStartTime = System.currentTimeMillis();
			
			//Wait for ackInit
			byte recBuff[] = new byte[140];
			this.hostUDPRecPacket = new DatagramPacket(recBuff, recBuff.length);
			
			//Wait for ack and try for n times 
			if(waitForRetransmission()){
				
				//set the current RTT
				this.RTT = System.currentTimeMillis() - this.sessionStartTime;
				
				//Check message
				if(checkConnMessage()){
					
					this.printDiagnosticMsg("Server is alive. Ack for Init received. Current RTT: "+this.RTT + " milliSecs.");
					
					//Send the client alive message
					temp = this.buildMsg(40, 0, (long)2, 
		                    			(long)0, (long)0, 
		                    			 0, 0, null);
					packageMsg(temp);	
					
					this.hostUDPSocket.send(this.hostUDPSendPacket);
					
					//Set the returnFlag to true
					retVal = true;
				}
				else
					throw new Exception("Incorrect message received"+
										"for Init conn message"+
									     this.timeOut);
				
			}else
				throw new Exception("Ack for Init msg timed out."
								    + "Current Time out: "+
									this.timeOut);

			
		} catch (IOException e) {

			e.printStackTrace();
		}
		
		return retVal;
	}
	
		
	//Wait and try n times for acknowledgement
	private boolean waitForRetransmission() throws IOException{
		boolean retVal = false;
		
		//Repeat the for the number of times of retransmissions
		for(int i = 0; i< this.numOfReTransmissions; ++i){
			
			try{
				hostUDPSocket.receive(this.hostUDPRecPacket);
				
				//Change the port of the server to port of the
				this.portOfServer = this.hostUDPRecPacket.getPort();
				retVal = true;
				break;
			}
			catch(SocketTimeoutException e1){
				
				this.printDiagnosticMsg("Timed out");
				
				//Double the timeout till time out  is less than 64 secs
				if(this.timeOut <= 64000 )
					this.timeOut = 2*this.timeOut; 
				else{
					//set the time out by formula based on RTT
					//or increase it by 5 
					this.timeOut = 5+this.timeOut;
				}
					
					
				hostUDPSocket.setSoTimeout((int)this.timeOut);
				
				//Resend the init message
				hostUDPSocket.send(this.hostUDPSendPacket);
				this.sessionStartTime = System.currentTimeMillis();
				
				this.printDiagnosticMsg("Time out increased to :"+this.timeOut);
				this.printDiagnosticMsg("Message resent");
				
			}
			
		}

		return retVal;
		
	}
	
	
	//Function to build the connection messages
	private void packageMsg(byte [] msgBuff){
		
		InetAddress ipOfServer;
		
		try {
			ipOfServer = InetAddress.getByName(this.ipAddress);
			
//			this.printDiagnosticMsg("Packaging message for server "+ ipOfServer);
			
			this.hostUDPSendPacket = new DatagramPacket(msgBuff, 
									 msgBuff.length, ipOfServer,
									 this.portOfServer);
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

	}
	
	
	//Check for return val of init Connection message
	private boolean checkConnMessage(){
		boolean retVal = false;
		
		byte[] msg = this.hostUDPRecPacket.getData();
		int mT = ByteBuffer.wrap(msg, 4, 4).getInt(); 
		long aN = ByteBuffer.wrap(msg, 16, 8).getLong();
		this.servRCWND = ByteBuffer.wrap(msg, 32, 4).getInt();
				
		//Check for init message type
		if(0 == mT && (long)1 == aN	)
			retVal = true;
		
		
		return retVal;
	}

	

}
