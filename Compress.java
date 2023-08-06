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

public class Compress implements Callable<byte[]> 
{
	public static final int BLOCK_SIZE = 131072;
	public byte[] blockbuffer;
	public byte[] dictbuffer;

	public Compress(byte[] blockbuffer, byte[] dictbuffer){
		this.blockbuffer = blockbuffer;
		this.dictbuffer = dictbuffer;
	}
    
	public byte[] call(){
		Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
		byte[] curbuffer = new byte[BLOCK_SIZE];
		def.setInput(blockbuffer);
		if (dictbuffer.length <= 0){
			byte[] temp = new byte[0];
			return temp;
		}
		else def.setDictionary(dictbuffer); 
		def.finish();
		int newlen = def.deflate(curbuffer, 0, curbuffer.length, Deflater.FULL_FLUSH);//s
		def.end();
		byte[] ret = new byte[newlen];
		for(int i=0;i<newlen;i++) ret[i] = curbuffer[i];
		return ret; 
	}
}