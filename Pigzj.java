import java.io.*;
import java.io.IOException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.concurrent.*;

public class Pigzj {
	public final static int BLOCK_SIZE = 131072;//1024*128
	public final static int DICT_SIZE = 32768;//1024*32
	private final static int GZIP_MAGIC = 0x8b1f;
	private final static int TRAILER_SIZE = 8;
	public static FileOutputStream writeData = new FileOutputStream(FileDescriptor.out);
	public static FileChannel writeInfo = new FileOutputStream(FileDescriptor.out).getChannel();
	public static CRC32 crc = new CRC32();

	public static void main(String[] args){ 
    	try{
			//parse -p argument
			int processors = Runtime.getRuntime().availableProcessors();
			for(int i=0;i<args.length;i++){
				if(args[i].equals( "-p")){
					try{
						if(Integer.parseInt(args[i+1])>processors*4) System.err.println("too many processors");
						else processors = Integer.parseInt(args[i+1]);
						i++;
					}
					catch(Exception e){
						System.err.println("bad -p argument");
						System.exit(1);
					}
				}
				else{
					System.err.println("Invalid arguments ");
					System.err.println("-p: use specified integer number of processors ");
					System.exit(1);
				}
			}
			crc.reset();
			//multithread
			ExecutorService executor = Executors.newFixedThreadPool(processors);
			List<Future<byte[]>> mylist = new ArrayList<Future<byte[]>>();
			int totallen = 0,blocksize = 0;
			BufferedInputStream stream = new BufferedInputStream(System.in);
			byte[] blockbuffer = new byte[BLOCK_SIZE];
			byte[] dictbuffer = new byte[DICT_SIZE];
			while(true){
				blocksize = stream.read(blockbuffer,0,BLOCK_SIZE);//read in the next block
				if(blocksize<BLOCK_SIZE){//last block
					if(blocksize>0){
						byte[] lastbuffer = new byte[blocksize];
						for(int i=0;i<blocksize;i++) lastbuffer[i] = blockbuffer[i];
						crc.update(lastbuffer, 0,blocksize);
						totallen += lastbuffer.length;
						Callable<byte[]> curthread = new Compress(lastbuffer,dictbuffer);
						Future<byte[]> s = executor.submit(curthread);
						mylist.add(s);
					}
					break;
				}
				crc.update(blockbuffer,0,blocksize); 
				totallen += blockbuffer.length;
				dictbuffer = new byte[DICT_SIZE];
				blockbuffer = new byte[BLOCK_SIZE];
				for(int i=0;i<DICT_SIZE;i++) dictbuffer[i] = blockbuffer[BLOCK_SIZE-DICT_SIZE+i];
				Callable<byte[]> curthread = new Compress(blockbuffer,dictbuffer);
				Future<byte[]> s = executor.submit(curthread);
				mylist.add(s);
			}
			executor.shutdown();
			//finish	
			writeHeader();
			for(int i=0;i<mylist.size();i++){
				Future<byte[]> fut = mylist.get(i);
				byte[] next = fut.get();
				try{
					writeData.write(next,0,next.length);
				}
				catch(Exception e){
					System.err.println("err");
					System.exit(1);
				}
			}
			byte[] temp = new byte[8];
			writeTrailer(temp,0,totallen);
			System.exit(0);
		}
		catch(Exception e){
			System.err.println("err");
			System.exit(1);
		}
	}

	public static void writeHeader(){
		try{
			byte[] b = new byte[] { (byte)(GZIP_MAGIC) ,(byte)(GZIP_MAGIC >> 8), Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, 0 };
			System.out.write(b);
		}
		catch(Exception e){
			System.err.println("err");
			System.exit(1);
		}
	}

	private static void writeTrailer(byte[] trailer, int offset, long len) throws IOException{
        writeInt((int) crc.getValue(), trailer, offset);
        writeInt((int) len, trailer, offset + 4);
		try{
			System.out.write(trailer);
		}
		catch(Exception e){
			System.err.println("err");
			System.exit(1);
		}
    }

    private static void writeInt(int i, byte[] trailer, int offset) throws IOException{
        writeShort(i & 0xffff, trailer, offset);
        writeShort((i >> 16) & 0xffff, trailer, offset + 2);
    }

    private static void writeShort(int s, byte[] trailer, int offset) throws IOException{
        trailer[offset] = (byte)(s & 0xff);
        trailer[offset + 1] = (byte)((s >> 8) & 0xff);
    }
}