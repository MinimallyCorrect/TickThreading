package nallar.nmsprepatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public class Main {
	public static void main(String[] args) {
		File patchDirectory = new File(args[0]);
		File sourceDirectory = new File(args[1]);
		PrePatcher.patch(patchDirectory, sourceDirectory);
	}
	
	/** 
	 * @param path name of the class, seperated by '/'
	 * @param bytes bytes of the class
	 * @return
	 */
	private static byte[] manipulateBinary(String path, byte[] bytes)
	{
	    // TODO: IMPLEMENT.
	    System.out.println("BINARY: "+path);
	    return bytes;
	}
	
	/** 
     * @param path name of the class, seperated by '/'
     * @param source of the class
     * @return
     */
    private static String manipulateSource(String path, String source)
    {
        // TODO: IMPLEMENT.
        System.out.println("SOURCE: "+path);
        return source;
    }
    
    
    /**
     * @param jar File
     * @param source if TRUE source, if FALSE binary
     */
    public static void editJar(File jar, boolean source) throws Exception
    {
        HashMap<String, byte[]> stuff = Maps.newHashMap();
        
        // READING
        JarInputStream istream = new JarInputStream(new FileInputStream(jar));
        JarEntry entry;
        while ((entry = istream.getNextJarEntry()) != null)
        {
            if (entry.getName().endsWith( source ? ".java" : ".class"))
            {
                // PARSING
                
                byte[] array = ByteStreams.toByteArray(istream);
                String name = entry.getName().replace('\\', '/');
                
                if (source)
                {
                    String str = new String(array, Charsets.UTF_8);
                    str = manipulateSource(name, str);
                    array = str.getBytes(Charsets.UTF_8);
                }
                else
                {
                    array = manipulateBinary(name, array);
                }
            }
            else
            {   
                stuff.put(entry.getName(), ByteStreams.toByteArray(istream));
            }
            
            istream.closeEntry();
        }
        istream.close();
        
        // WRITING
        JarOutputStream ostream = new JarOutputStream(new FileOutputStream(jar));
        for (Entry<String, byte[]> e : stuff.entrySet())
        {
            ostream.putNextEntry(new JarEntry(e.getKey()));
            ostream.write(e.getValue());
            ostream.closeEntry();
        }
        ostream.close();
    }
}
