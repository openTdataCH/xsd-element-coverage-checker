import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.transform.stream.StreamSource;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaObject;

public final class XMLSchemaUtils {

    private XMLSchemaUtils() {
    }

    /**
     * Auxiliary method to get the xsd/xml file or a subceding folder for the given folder with the given name.
     *
     * @param folderName the base folder containing the given xsd/xml file
     * @param fileName the file name in the folder pointing to the xsd/xml file
     * @return the xsd/xml File
     */
    public static File openFileOrFolder(String folderName, String fileName) {
        File file = null;

        if (fileName != null) {
            file = new File(folderName + fileName);
        } else {
            file = new File(folderName);
        }
        if (!file.exists()) {
            System.out.println("File/Folder does not exist: " + folderName + fileName);
            System.exit(1);
        }
        return file;
    }

    /**
     * This auxiliary method returns the list of xml/xsd schema objects in the given xml/xsd file and folder
     *
     * @param rootFile the file to open and get the schema data out of
     * @param folderName the base folder containing the given xsd/xml file
     * @return (possibly empty) List of XMLSchemaObject
     * @throws FileNotFoundException
     */
    public static List<XmlSchemaObject> getSchemaItems(File rootFile, String folderName) throws FileNotFoundException {
        XmlSchemaCollection schemaCollection = new XmlSchemaCollection();
        schemaCollection.setBaseUri(folderName);
        XmlSchema schema = schemaCollection.read(new StreamSource(new FileInputStream(rootFile)));

        // Get all items
        return schema.getItems();
    }

    /**
     * This method checks if we have a full subpath.
     * <p>
     * We use the "/" delimiter as indicator.
     * <p>
     * Examples: "/OFPFare" is a full subpath of "/OJPFare" <br> "/OJP" is NOT a full subpath of "/OJPFare" <br> "/OFPFare/bar" is a full subpath of "/foo/OJPFare/bar" <br> "/OFPFare" is a full
     * subpath of "/foo/OJPFare/bar" <br> "/OJP" is NOT a full subpath of "/foo/OJPFare/bar" <br> "/OJPFare/bar/blu" is not a full subpath of "/foo/OJPFare/bar"
     *
     * @param string the string to have the full subpath
     * @param subPath the subpath to be fully contained in the string
     * @return true if full subpath
     */
    public static boolean fullSubpath(String string, String subPath, String delimiter) {
        // If the subpath is not part of the string we stop
        if (string.contains(subPath)) {
            // We throw away anything before the relevant part in the string.
            int startIndex = string.indexOf(subPath);
            String subString = string.substring(startIndex);

            // Extract the same amount of delimiters from the string as they exist in the substring
            // If that's not possible then we do not have a full subpath, because the path is longer
            // than the substring's path
            String[] subPathParts = subPath.split(delimiter);
            String[] subStringParts = subString.split(delimiter);
            if (subStringParts.length < subPathParts.length) {
                return false;
            }

            // All parts of the subpath must be in the substring
            for (int i = 0; i < subPathParts.length; i++) {
                if (!subPathParts[i].equals(subStringParts[i])) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    /**
     * This code writes the output to the given path: col1: path, col2: schema type, col3: schema name, col4: references
     *
     * @param outputFilePath the path to the output file
     * @throws IOException
     */
    public static File printBitmapToCsv(String outputFilePath, Map<String, Map<String, Set<String>>> bitmap) throws IOException {
        FileWriter csvFileWriter = new FileWriter(outputFilePath);
		csvFileWriter.append("File;Type;Pseudo_path;;Covering_examples\n");
        for (Entry<String, Map<String, Set<String>>> entry : bitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<String, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("element").append(File.pathSeparator).append(elementEntry.getKey())
                        .append(File.pathSeparator)
                        .append(elementEntry.getValue().toString())
                        .append(System.lineSeparator());
                }
            } else {
                csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("N/A").append(File.pathSeparator).append("N/A").append(File.pathSeparator)
                    .append("N/A")
                    .append(System.lineSeparator());
            }
        }

        return new File(outputFilePath);
    }

    /**
     * This auxiliary method creates a deep copy of the given bitmaps
     *
     * @param bitmap to deep copy
     * @return deep copy
     */
    public static Map<String, Map<String, Set<String>>> createBitmapDeepCopy(Map<String, Map<String, Set<String>>> bitmap) {
        Map<String, Map<String, Set<String>>> bitmapDeepCopy = new HashMap<>();

        for (String key : bitmap.keySet()) {

            Map<String, Set<String>> subBitmapDeepCopy = new HashMap<>();

            if (bitmap.get(key) == null) {
                subBitmapDeepCopy = null;
            } else {
                for (String subKey : bitmap.get(key).keySet()) {
                    Set<String> subBitmapSetDeepCopy = new HashSet<>();

                    for (String subKeyValue : bitmap.get(key).get(subKey)) {
                        subBitmapSetDeepCopy.add(subKeyValue);
                    }

                    subBitmapDeepCopy.put(subKey, subBitmapSetDeepCopy);
                }
            }

            bitmapDeepCopy.put(key, subBitmapDeepCopy);
        }

        return bitmapDeepCopy;
    }

}
