import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class TFTPServer {
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "C:/Users/ghiya/OneDrive - student.lnu.se/Desktop/";
	public static final String WRITEDIR = "C:/Users/ghiya/OneDrive - student.lnu.se/Desktop/";
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		// Starting the server
		try {
			TFTPServer server = new TFTPServer();
			server.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void start() throws SocketException {
		byte[] buf = new byte[BUFSIZE];

		// Create socket
		DatagramSocket socket = new DatagramSocket(null);

		// Create local bind point
		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests
		try {
			while (true) {
				final InetSocketAddress clientAddress = receiveFrom(socket, buf);
				// If clientAddress is null, an error occurred in receiveFrom()
				if (clientAddress == null)
					continue;

				final StringBuffer requestedFile = new StringBuffer();
				final int reqtype = ParseRQ(buf, requestedFile);

				new Thread() {
					public void run() {
						try {
							DatagramSocket sendSocket = new DatagramSocket(0);

							// Connect to client
							sendSocket.connect(clientAddress);

							System.out.printf("%s request from %s using port %d\n",
									(reqtype == OP_RRQ) ? "Read" : "Write",
									clientAddress.getHostName(), clientAddress.getPort());

							// Read request
							if (reqtype == OP_RRQ) {
								requestedFile.insert(0, READDIR);
								handleRequest(sendSocket, requestedFile.toString(), OP_RRQ, clientAddress.getPort());
							}
							// Write request
							else {
								requestedFile.insert(0, WRITEDIR);
								handleRequest(sendSocket, requestedFile.toString(), OP_WRQ, clientAddress.getPort());
							}
							sendSocket.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf    (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws Exception {

		// Create datagram packet
		DatagramPacket packet = new DatagramPacket(buf, buf.length);
		// Receive packet
		socket.receive(packet);
		// Get client address and port from the packet
		return new InetSocketAddress(packet.getAddress(), packet.getPort());
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 *
	 * @param buf           (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
		int opcode = (buf[0] << 8) | (buf[1] & 0x00ff);
		if (opcode == 1 || opcode == 2) {
			int position = insertData(2, buf, requestedFile);
			StringBuffer md = new StringBuffer();
			insertData(position, buf, md);
		}
		return opcode;
	}

	private int insertData(int position, byte[] buf, StringBuffer requestedFile) {
		for (int i = position; i < buf.length; i++) {
			byte b = buf[i];
			if (b == 0) {
				return i + 1;
			} else {
				requestedFile.append((char) b);
			}
		}
		return -1; // signal that null terminator was not found
	}

	/**
	 * Handles RRQ and WRQ requests
	 *
	 * @param sendSocket    (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode        (RRQ or WRQ)
	 */

	private void handleRequest(DatagramSocket sendSocket, String requestedFile, int opcode, int port) throws IOException {
		if (opcode == OP_RRQ) {
			handleReadRequest(sendSocket, requestedFile, port);
		} else if (opcode == OP_WRQ) {
			handleWriteRequest(sendSocket, requestedFile, port);
		} else {
			System.err.println("Invalid request. Sending an error packet.");
			send_ERR(sendSocket, 0, requestedFile);
		}
	}

	private void handleReadRequest(DatagramSocket sendSocket, String requestedFile, int port) throws IOException {
		File file = new File(requestedFile);
		if (!(file.exists() && file.isFile())) {
			System.out.println("The file was not found");
			return;
		}
		byte[] bytes = Files.readAllBytes(Paths.get(requestedFile));
		int block = 1;
		int blockLength = (bytes.length / 512) + 1;
		int ind = 0;
		byte[] buffer = collectInformation(block, bytes);
		while (block <= blockLength) {
			int result = send_DATA_receive_ACK(sendSocket, buffer, port);
			if (result > 0) {
				if (result == block) {
					block++;
					ind = 0;
					if (block <= blockLength) {
						buffer = collectInformation(block, bytes);
					}
				} else if (result + 1 <= blockLength) {
					buffer = collectInformation(result + 1, bytes);
					ind++;
				}
			} else {
				ind++;
			}
		}
	}

	private void handleWriteRequest(DatagramSocket sendSocket, String requestedFile, int port) throws IOException {
		File file = new File(requestedFile);
		if (file.exists() && file.isFile()) {
			System.err.println("The file exists already");
			return;
		}
		try (FileOutputStream fileOutputStream = new FileOutputStream(requestedFile);
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			int block = 0;
			int problem = 0;
			ByteBuffer allocate = ByteBuffer.allocate(4);
			allocate.putShort((short) OP_ACK);
			allocate.putShort((short) block);
			byte[] ack = allocate.array();
			int ind = 0;
			while (true) {
				int result = 0;
				if ((result = receive_DATA_send_ACK(sendSocket, ack, outputStream, port)) < 0) {
					problem++;
				} else {
					problem = 0;
					ind += result;
				}
				if (result >= 512 || result == -1) {
					continue;
				}
				break;
			}
			if (problem < 3) {
				fileOutputStream.write(outputStream.toByteArray());
				fileOutputStream.flush();
				sendSocket.send(new DatagramPacket(ack, ack.length));
			}
		}
	}

	private byte[] collectInformation(int block, byte[] information) {
		final int blockSize = 512;
		int offset = blockSize * (block - 1);
		int length = Math.min(blockSize, information.length - offset);
		byte[] data = Arrays.copyOfRange(information, offset, offset + length);
		ByteBuffer buffer = ByteBuffer.allocate(length + 4);
		buffer.putShort((short) OP_DAT);
		buffer.putShort((short) block);
		buffer.put(data);
		return buffer.array();
	}

	private int send_DATA_receive_ACK(DatagramSocket socket, byte[] bytes, int port) {
		DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length);
		DatagramPacket receivePacket = new DatagramPacket(new byte[BUFSIZE], BUFSIZE);
		try {
			socket.send(sendPacket);
			socket.receive(receivePacket);
			if (receivePacket.getPort() != port) {
				return -2;
			}
			int op_code = ParseRQ(receivePacket.getData(), null);
			if (op_code == OP_ACK) {
				return ((receivePacket.getData()[2] << 8) | (receivePacket.getData()[3] & 0x00ff));
			} else {
				return -1;
			}
		} catch (SocketTimeoutException te) {
			return -1;
		} catch (IOException e) {
			return -1;
		}
	}

	private int receive_DATA_send_ACK(DatagramSocket socket, byte[] ack, ByteArrayOutputStream note,
			int port) {
		try {
			socket.send(new DatagramPacket(ack, ack.length));
			byte[] bytes = new byte[BUFSIZE];
			DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
			socket.receive(packet);

			if (packet.getPort() == port) {
				int op_code = ParseRQ(bytes, null);
				if (op_code == OP_DAT) {
					byte[] information = Arrays.copyOfRange(packet.getData(), 4, packet.getLength());
					note.write(information);
					note.flush();
					if (information.length < 512) {
						socket.close();
					}
					byte[] ackBytes = ByteBuffer.allocate(4).putShort((short) OP_ACK).putShort((short) (op_code + 1)).array();
					socket.send(new DatagramPacket(ackBytes, ackBytes.length, packet.getAddress(), packet.getPort()));
					return information.length;
				} else {
					return -1;
				}
			} else {
				return -2;
			}
		} catch (IOException e) {
			return -1;
		}
	}

  private void send_ERR(DatagramSocket sendSocket, short errorcode, String errormsg) {
  
    // create array which holds error opcode and type of error number
    byte[] sendError = new byte[BUFSIZE - 4];
    short shortVal = OP_ERR;
    short val = errorcode;
    
    System.out.println("Error occured, error code: " + errorcode + " sent to client.");
  
    // create ByteBuffer containing error information to send to client
    ByteBuffer wrap = ByteBuffer.wrap(sendError);
    wrap.putShort(shortVal);
    wrap.putShort(val);
    stringToByte(errormsg, sendError);
    wrap.put((byte)0);
    
    DatagramPacket errorPackage = new DatagramPacket(sendError, sendError.length);
    try{
      
      // send error package
      sendSocket.send(errorPackage);
    } catch (IOException e){
      System.err.println("Problem with sending error..!");
      e.getMessage();
    }
  
    
  }
}