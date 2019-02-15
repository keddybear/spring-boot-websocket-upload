package com.example.client.websocket;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.Future;

import org.eclipse.jetty.websocket.api.Session;

/*
 * Author: Dikai Xiong
 * Date: 2/11/2019
 */

/*
 * UploadRunnable
 * 
 * 1. It creates a new Thread to upload a single file in small chunks.
 * 2. After the last chunk is sent, it will send the boundary specified by ClientUploadSocket.
 */
public class UploadRunnable implements Runnable {
	
	private Session session;
	private String fileToUpload;
	private String assetFolder = "./src/main/assets/";
	private String boundary;
	private int maxUploadSize = 8 * 1024; // I can't seem to make it bigger
	
	public UploadRunnable(String file, Session session, String boundary) {
		this.fileToUpload = file;
		this.session = session;
		this.boundary = boundary;
	}

	public void run() {
		uploadInSmallChunks(this.fileToUpload);
	}
	
	private void uploadInSmallChunks(String filename) {
		try {
			RandomAccessFile aFile = new RandomAccessFile(assetFolder + filename, "r");
	        FileChannel inChannel = aFile.getChannel();
	        MappedByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
	        buffer.load();
	        while (buffer.hasRemaining()) {
	        	Future<Void> result;
	        	int size = Math.min(maxUploadSize, buffer.remaining());
	        	byte[] chunk = new byte[size];
	        	buffer.get(chunk, 0, size);
				result = session.getRemote().sendBytesByFuture(ByteBuffer.wrap(chunk));
				result.get();
	        }
	        // Send boundary
	        Future<Void> fu;
	        fu = session.getRemote().sendBytesByFuture(ByteBuffer.wrap(this.boundary.getBytes()));
	        fu.get();
	        // Clear and close file
	        buffer.clear();
	        inChannel.close();
	        aFile.close();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

}
