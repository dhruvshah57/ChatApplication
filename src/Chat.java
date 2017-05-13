
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Chat {

	static int id=1;
	static List<Peer> peers=new ArrayList<>();
	static Selector read;
	static Selector write;
	static List<SocketChannel> openChannels=new ArrayList<>();
	static String myip;
	static Client client;
	static Server server;
	
	public static void main(String[] args) throws IOException {

		
		read=Selector.open();
		write =Selector.open();
		
		if(args[0]==null)
		{
			System.out.println("Usage <port number>");
			System.exit(1);
		}
		else
		{
			server=new Server(Integer.parseInt(args[0]));
			server.start();
			client=new Client();
			client.start();
		}
		Scanner input = new Scanner(System.in);
		boolean run=true;
		System.out.print("Welcome\n");
		while (run) {
			String str = input.nextLine();
			String[] arrStr = str.split(" ");
			String command=arrStr[0];
			switch(command)
			{
			case "help":
				System.out.println("myip\tDisplay Ip address");
				System.out.println("myport\tDisplay port number");
				System.out.println("connect\tConnect to peer\t\tconnect<destination Ip> <destination IP>");
				System.out.println("list\tList all connected peers");
				System.out.println("send\tSend a message\t\tsend <connection_id> <message>");
				System.out.println("terminate\tTerminate the connection\tterminate <connection_id>");
				System.out.println("exit\tExit the program");
				break;
			case "myip":
				myip();
				break;
			case "myport":
				System.out.println("The program runs on port number "+args[0]);
				break;
			case "connect":
				//client part
				//System.out.println(arrStr[1]);
				if(arrStr.length<3)
				{
					System.out.println("syntax problem");
					break;
				}
				connect(arrStr[1],arrStr[2]);
				break;
			case "list":
				System.out.println("Id:\tIP Address\t\tPort number\n");
				for(Peer peer:peers)
				{
					System.out.println(peer.getId()+":\t"+peer.getIp()+"\t\t"+peer.getPort());
				}
				break;
			case "send":
				if(arrStr.length<3)
				{
					System.out.println("syntax problem");
					break;
				}
				String message="";
				for(int i=2;i<arrStr.length;i++)
				{
					message+=" "+arrStr[i];
				}
				send(Integer.parseInt(arrStr[1]), message);
				break;
			case "terminate":
				if(arrStr.length<2)
				{
					System.out.println("syntax problem");
					break;
				}
				terminate(Integer.parseInt(arrStr[1]));
				break;
			case "exit":
				run=false;
				System.out.println("Thank you Bye!..");
				System.exit(1);
				break;
			}

		}
		input.close();
	}
	
	public static void connect(String ip,String port)
	{
		System.out.println("connecting to "+ip);
		try {
			if(!ip.equals(myip))
			{
				for(Peer peer:peers)
				{
					if(peer.getIp().equals(ip))
					{
						System.out.println(ip+" already connected");
						return;
					}
				}
				SocketChannel socketChannel = SocketChannel.open();
				socketChannel.connect(new InetSocketAddress(ip, Integer.parseInt(port)));
				socketChannel.configureBlocking(false);
				socketChannel.register(read, SelectionKey.OP_READ );
				socketChannel.register(write, SelectionKey.OP_WRITE );
				openChannels.add(socketChannel);
				id++;
				peers.add(new Peer(id,ip, Integer.parseInt(port)));
				System.out.println("The connection to peer "+ip+" is successfully established");
			}
			else
			{
				System.out.println("you can't connect to yourself or peer already connected");
			}
			
		} catch (NumberFormatException | IOException e) {
			System.out.println("error in connection");
		}
	}
	
	public static void send(Integer id, String message)
	{
		int channelReady=0;
		try{
			channelReady=write.select();
			if(channelReady>0)
			{
				Set<SelectionKey> keys=write.selectedKeys();
				Iterator<SelectionKey> selectedKeysIterator = keys.iterator();
				ByteBuffer buffer = ByteBuffer.allocate(100);
				buffer.put(message.getBytes());
				buffer.flip();
				
				while(selectedKeysIterator.hasNext())
				{
					SelectionKey selectionKey=selectedKeysIterator.next();
					if(parseChannelIp((SocketChannel)selectionKey.channel()).equals(getPeer(id).getIp()))
					{
						SocketChannel socketChannel=(SocketChannel)selectionKey.channel();
						socketChannel.write(buffer);
					}
					selectedKeysIterator.remove();
				}
			}
		}catch(Exception e)
		{
			System.out.println("sending fail because"+e.getMessage());
		}
	}
	
	public static void terminate(Integer id)
	{
		try
		{
				Peer peer=getPeer(id);
				for(SocketChannel channel:openChannels)
				{
					if(peer.getIp().equals(parseChannelIp(channel)))
					{
						channel.close();
						openChannels.remove(channel);
						break;
					}
				}
				peers.remove(peer);
				System.out.println("terminated connection "+id);
			
		}catch(Exception e)
		{
			System.out.println("termination failed due to "+e.getMessage());
		}
	}
	
	public static Peer getPeer(int id)
	{
		for(Peer peer:peers)
		{
			if(peer.getId()==id)
			{
				return peer;
			}
		}
		return null;
	}
	public static String parseChannelIp(SocketChannel channel){//parse the ip form the SocketChannel.getRemoteAddress();
		String ip = null;
		String rawIp =null;  
		try {
			rawIp = channel.getRemoteAddress().toString().split(":")[0];
			ip = rawIp.substring(1, rawIp.length());
		} catch (IOException e) {
			System.out.println("can't convert channel to ip");
		}
		return ip;
	}
	public static Integer parseChannelPort(SocketChannel channel){//parse the ip form the SocketChannel.getRemoteAddress();
		String port =null;  
		try {
			port = channel.getRemoteAddress().toString().split(":")[1];
		} catch (IOException e) {
			System.out.println("can't convert channel to ip");
		}
		return Integer.parseInt(port);
	}
	
	public static Peer getIpFromPeers(String ip)
	{
		for(Peer peer:peers)
		{
			if(peer.getIp().equals(ip))
			{
				return peer;
			}
		}
		return null;
	}
	
	
	@SuppressWarnings("rawtypes")
	public static void myip() throws SocketException, UnknownHostException
	{
		/*Enumeration e = NetworkInterface.getNetworkInterfaces();
		boolean flag=false;
		while(e.hasMoreElements() && !flag)
		{
		    NetworkInterface n = (NetworkInterface) e.nextElement();
		    Enumeration ee = n.getInetAddresses();
		    int round =0;
		   
		    while (ee.hasMoreElements() && !flag)
		    {
		        InetAddress i = (InetAddress) ee.nextElement();
		        if(round == 1)
		        {
		        	System.out.println("The IP Address is "+i.getHostAddress());
		        	myip=i.getHostAddress();
		        	flag=true;
		        }
		        round++;
		    }
		}*/
		System.out.println("The IP Address is "+InetAddress.getLocalHost().getHostAddress());
	}
}

class Peer{
	
	private int id;
	private String ip;
	private int port;
	
	Peer(int id,String ip, int port)
	{
		this.id=id;
		this.ip=ip;
		this.port=port;
	}

	public int getId() {
		return id;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}
	
}
class Server extends Thread {
	
	int port=0;
	
	
	Server(int port)
	{
		this.port=port;
	}
	public void run()
	{
		try {
			Chat.read=Selector.open();
			Chat.write=Selector.open();
			ServerSocketChannel serverSocketChannel=ServerSocketChannel.open();
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.bind(new InetSocketAddress(port));
			while(true)
			{
				SocketChannel socketChannel=serverSocketChannel.accept();
				if(socketChannel != null)
				{
					socketChannel.configureBlocking(false);
					socketChannel.register(Chat.read, SelectionKey.OP_READ);
					socketChannel.register(Chat.write, SelectionKey.OP_WRITE);
					Chat.openChannels.add(socketChannel);
					int id=Chat.id++;
					Chat.peers.add(new Peer(id++,Chat.parseChannelIp(socketChannel), Chat.parseChannelPort(socketChannel)));
					System.out.println("The connection to peer "+Chat.parseChannelIp(socketChannel)+" is succesfully established");
				}
			}
		}
		catch(BindException ex){
    		System.out.println("Can not use port " + this.port + ". Please choose another port and restart\n");
    		System.exit(1);
		}catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
}

class Client extends Thread{
	
	Set<SelectionKey> keys;
	Iterator<SelectionKey> selectedKeysIterator;
	ByteBuffer buffer=ByteBuffer.allocate(100);
	SocketChannel socketChannel;
	int byteRead;
	public void run()
	{
		try{
			while(true)
			{
				int channelReady=Chat.read.selectNow();
				keys=Chat.read.selectedKeys();
				selectedKeysIterator=keys.iterator();
				if(channelReady != 0)
				{
					while(selectedKeysIterator.hasNext())
					{
						SelectionKey key=selectedKeysIterator.next();
						socketChannel=(SocketChannel)key.channel();
						try{
							byteRead=socketChannel.read(buffer);
						}catch(IOException e)
						{
							selectedKeysIterator.remove();
							String IP=Chat.parseChannelIp(socketChannel);
							Peer peer=Chat.getIpFromPeers(IP);
							Chat.terminate(peer.getId());
							System.out.println(IP+" remotely close the connection");
							break;
						}
						
						
						String message="";
						while(byteRead != 0)
						{
							buffer.flip();
							while(buffer.hasRemaining())
							{
								message+=((char)buffer.get());
							}
							message=message.trim();
							if(message.isEmpty())
							{
								String IP=Chat.parseChannelIp(socketChannel);
								Peer peer=Chat.getIpFromPeers(IP);
								Chat.terminate(peer.getId());
								System.out.println("Peer "+IP + " terminates the connection");	
								break;
							}
							else
							{
								System.out.println("Message received from "+Chat.parseChannelIp(socketChannel)+": "+message);
							}
							buffer.clear();
							if(message.trim().isEmpty())
								byteRead =0;
							else
								byteRead = socketChannel.read(buffer);
						
							byteRead=0;
							selectedKeysIterator.remove();
						}
					}
				}
			}
			
		}catch(Exception e)
		{
			System.out.println("something wrong went client side");
		}
	}
}
