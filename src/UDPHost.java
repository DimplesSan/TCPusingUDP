import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Set;

public abstract class UDPHost {
	
	protected boolean diagnosticFlag;
	protected int udpPort;
	protected DatagramSocket hostUDPSocket;
	protected DatagramPacket hostUDPSendPacket;
	protected DatagramPacket hostUDPRecPacket;
	
	public abstract void begin() throws Exception;
	
	
	
	
	public void printDiagnosticMsg(String msg){
		
		//Check if the diago
		if(!this.diagnosticFlag)
			System.out.println(msg);
	}
	
	
	
	//Check for the existence of a value in a set
	public boolean checkValInSet(int val, Set<Integer> valSet){
		
		boolean retVal = false;
		
		for(int v : valSet){
			if(v == val ){
				retVal = true;
				break;
			}
		}
		
		return retVal;
	}
	
	
	
	
	//Header length - int - 4 bytes - 40 bytes of header (0 - 3)
	//Message type - int 4 bytes - Init(0) / StartFT(1) / EndFT(2) /hash(3) / Close(4) --> (4 to 7)
	//Seq Num - long 8 bytes --> (8 to 15)
	//Ack Num - long 8 bytes --> (16 to 23)
	//CheckSum for payload - long 8 bytes --> (24 to 31)
	//ReceiveWindow - int 4 bytes --> (32 to 35)
	//Payload length - int 4 bytes --> (36 to 39)
	//Payload - 100 bytes
	protected byte[] buildMsg(int hdrLen, int msgTyp,
							  long seqN, long ackN,
							  long chkSum, int rcwnd,
							  int payLdLen, byte [] payLd 
						     ){
		
		//Return Buffer
		byte[] msgBuffer = new byte[hdrLen + payLdLen];
		int i = 0;
		
		//Add the headerLen to msgBuffer
		byte temp[] = ByteBuffer.allocate(4).putInt(hdrLen).array();
		for(i= 0; i<temp.length; ++i )
			msgBuffer[i] = temp[i];
		
//		System.out.println("i: "+ i+" hdrLen: " +hdrLen +" MsgBuffer : "+ ByteBuffer.wrap(msgBuffer, 0, 4).getInt());
		
		//Add the msg type to msgBuffer 
		temp = ByteBuffer.allocate(4).putInt(msgTyp).array();
		for(int j = 0; j<temp.length; ++j )
			msgBuffer[i++] = temp[j];
		
//		System.out.println("i: "+ i+" msgTyp: " +msgTyp +" MsgBuffer : "+ ByteBuffer.wrap(msgBuffer, 4, 4).getInt());
		
		//Add the seq number
		temp  = ByteBuffer.allocate(8).putLong(seqN).array();
		for(int j = 0; j<temp.length; ++j )
			msgBuffer[i++] = temp[j];
		
//		System.out.println("i: "+ i+" seqN: " +seqN +" MsgBuffer : "+ ByteBuffer.wrap(msgBuffer, 8, 8).getLong());
		
		//Add the ack number
		temp  = ByteBuffer.allocate(8).putLong(ackN).array();
		for(int j = 0; j<temp.length; ++j )
			msgBuffer[i++] = temp[j];
		
//	System.out.println("i: "+ i+" ackN: " +ackN +" MsgBuffer : "+ ByteBuffer.wrap(msgBuffer, 16, 8).getLong());
		
		
		//Add the chkSum
		temp  = ByteBuffer.allocate(8).putLong(chkSum).array();
		for(int j = 0; j<temp.length; ++j )
			msgBuffer[i++] = temp[j];

//		System.out.println("i: "+i+" chkSum: " +chkSum +" MsgBuffer : "+ ByteBuffer.wrap(msgBuffer, 24, 8).getLong());
		
		
		//Add the rcwnd
		temp  = ByteBuffer.allocate(4).putInt(rcwnd).array();
		for(int j = 0; j<temp.length; ++j )
			msgBuffer[i++] = temp[j];
		
//		System.out.println("i: "+ i+" rcwnd: " +rcwnd +" MsgBuffer : "+ ByteBuffer.wrap(msgBuffer, 32, 4).getInt());
		
		
		//Add the payLdLen
		temp  = ByteBuffer.allocate(4).putInt(payLdLen).array();
		for(int j = 0; j<temp.length; ++j )
			msgBuffer[i++] = temp[j];
		
//		System.out.println("i: "+ i+" payLdLen: " +payLdLen +" MsgBuffer : "+ ByteBuffer.wrap(msgBuffer, 36, 4).getInt());
		
		
		//Add the payLoad
		if(payLd != null){
			for(int j = 0; j<payLd.length; ++j )
				msgBuffer[i++] = payLd[j];
			
//			System.out.println("payLdLen: " +payLdLen +" MsgBuffer : "+ ByteBuffer.wrap(temp).getInt());
		}
		
		
		return msgBuffer;
		
	}
	
	

	
}
