import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.io.FilenameUtils;
import org.apache.ws.commons.schema.XmlSchema;
import org.apache.ws.commons.schema.XmlSchemaCollection;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaInclude;
import org.apache.ws.commons.schema.XmlSchemaObject;

/**
 * This code allows to check how much of the XSD schema in xsdMain are covered by the example XML files in the xmlMain folder.
 * <p>
 * The xsdMainFileName is the root schema file included in all the examples to use for the coverage check.
 * <p>
 * The outputFile is where the coverage is written into.
 * <p>
 * For verbose output set the print attribute to true.
 * <p>
 * We focus on XML Elements for now, no complex types, groups, etc. We also ignore abstract="true" elements.
 * <p>
 * How it works: 1. we create a large bitmap (elementBitmap): xsdFilePath -> (XSD Element to check ->  Set of XML files having elements matching the XSD element) 2. we iterate through all XML files
 * and compare each element within against the xsd elements and if they match add the file's path to the set 3. we print it all into a xsd file with columns: xsd file; xsd element (N/A if the xsd had
 * no element (e.g., only referecnces)); set of files using the element (N/A if the previous one was N/A)
 */
public final class XMLSchemaCoverageChecker {

    // Implicit Bitmap for elements parsed out of the various schema, <file, <element, list of usages>>
    private static Map<String, Map<XmlSchemaElement, Set<String>>> elementBitmap = new HashMap<>();

    private static List<String> ignoredFiles = new ArrayList<>();

    // Starting point (file) for parsing the schema and its relative (base) path in the xsd folder
    private static String xsdMainFileName = "NeTEx_publication.xsd";
    // Insert the path to the main xsd folder
    private static String xsdMain = "C:/Users/.../Desktop/dev_analysis/NeTEx/xsd/";
    // Insert the path to the main examples folder
    private static String xmlMain = "C:/Users/.../Desktop/dev_analysis/NeTEx/examples/";
    // Insert the path to the output csv file
    private static String outputFilePath = "C:/Users/.../Desktop/dev_analysis/homework/xsdCoverage.csv";

    private static boolean print = false;

    private XMLSchemaCoverageChecker() {
    }

    public static void main(String[] args) throws URISyntaxException, IOException, ConfigurationException {
        // Load a bitmap of all elements
        System.out.println("Loading the XSDs into memory.");
        loadXsd(xsdMain, xsdMainFileName);

        // Go example by example and check the coverage accross the schema.
        System.out.println("Checking the XMLs against the XSDs.");
        checkXml(xmlMain);

        // Print the results
        System.out.println("Writing the result to disk");
        printToCsv();

        // Print which files were ignored
        if (print) {
            System.out.println("Ignored the following files: " + Arrays.toString(ignoredFiles.toArray()));
        }
    }

    /**
     * A recursive method to populate the initial bitmap of elements.
     *
     * @param folderName the folder to traverse for the schema
     * @param fileName the file to traverse from
     * @throws URISyntaxException
     * @throws FileNotFoundException
     */
    private static void loadXsd(String folderName, String fileName) throws URISyntaxException, IOException {
        // Get the schema
        XmlSchemaCollection schemaCollection = new XmlSchemaCollection();
        schemaCollection.setBaseUri(folderName);

        XmlSchema schema = schemaCollection.read(new StreamSource(new FileInputStream(new File(folderName + fileName))));

        // Get all items
        List<XmlSchemaObject> schemaItems = schema.getItems();

        // Prepare the mapping on this level
        Map<XmlSchemaElement, Set<String>> fileBitmap = new HashMap<>();

        for (XmlSchemaObject schemaItem : schemaItems) {
            // For all xml schema elements, do a recursive element resolvance.
            if (schemaItem instanceof XmlSchemaElement) {
                // Ensure it's not an abstract element
                if (!((XmlSchemaElement) schemaItem).isAbstractElement()) {
                    fileBitmap.put((XmlSchemaElement) schemaItem, new HashSet<String>());

                    elementBitmap.put(folderName + fileName, fileBitmap);
                }
            }
            // For all includes call this method recursively
            else if (schemaItem instanceof XmlSchemaInclude) {
                // Get the relative location of the file to be loaded
                String schemaLocation = ((XmlSchemaInclude) schemaItem).getSchemaLocation();

                // Differentiate if we have a file in the same or a different folder
                if (schemaLocation.contains("/")) { // indicates a different folder
                    // Resolve the folder path
                    File schemaFile = new File(folderName + schemaLocation);
                    File schemaFolder = new File(schemaFile.getParent());

                    // If we've already been there don't go there
                    boolean beenThere = false;

                    for (String key : elementBitmap.keySet()) {
                        if (key.contains(schemaFile.getCanonicalPath())) {
                            beenThere = true;
                        }
                    }

                    if (beenThere) {
                        continue;
                    }

                    // Remember the included location so that we don't go there again.
                    elementBitmap.put(schemaFile.getCanonicalPath(), null);

                    if (print) {
                        System.out.println("Element bitmap size is now: " + elementBitmap.size() + " added: " + schemaFile.getCanonicalPath());
                    }

                    loadXsd(schemaFolder.getCanonicalPath() + File.separator, schemaFile.getName());
                } else {
                    // If we've already been there don't go there
                    boolean beenThere = false;

                    for (String key : elementBitmap.keySet()) {
                        if (key.contains(folderName + schemaLocation)) {
                            beenThere = true;
                        }
                    }

                    if (beenThere) {
                        continue;
                    }

                    elementBitmap.put(folderName + schemaLocation, null);

                    if (print) {
                        System.out.println("Element bitmap size is now: " + elementBitmap.size() + " added: " + folderName + schemaLocation);
                    }

                    loadXsd(folderName, schemaLocation);
                }
            }
        }
    }

    /**
     * A recursive method to traverse the examples and to add each example to the bitmap that utilized a specific XML element.
     *
     * @param folderName the folder containing the xml files to check
     */
    private static void checkXml(String folderName) throws IOException, ConfigurationException {
        File xmlFolder = new File(folderName);

        for (File fileOrFolder : xmlFolder.listFiles()) {
            if (fileOrFolder.isFile() && "xml".equals(FilenameUtils.getExtension(fileOrFolder.getCanonicalPath()))) {
                // If it's a file let's check it
                if (print) {
                    System.out.println("Checking file: " + fileOrFolder.getCanonicalPath());
                }

                // Alot of code for getting the xml DOM
                Parameters params = new Parameters();
                FileBasedConfigurationBuilder<XMLConfiguration> builder =
                    new FileBasedConfigurationBuilder<XMLConfiguration>(XMLConfiguration.class)
                        .configure(params.xml().setFile(fileOrFolder));
                XMLConfiguration config = builder.getConfiguration();

                // Now, we get the actual content and check it
                checkElement(fileOrFolder, config.getNodeModel().getRootNode());
            } else if (fileOrFolder.isDirectory()) {
                // If it's a folder let's recurse
                checkXml(fileOrFolder.getCanonicalPath());
            } else {
                ignoredFiles.add(fileOrFolder.getCanonicalPath());
            }
        }
    }

    /**
     * This method recursively checks an element and all its sub-elements for their match with our element bitmap.
     *
     * @param file the example file we're checking, this is mainly needed to reference the xml that covered the given xsd
     * @param rootNode the node to check for existence against the xsd schema
     */
    private static void checkElement(File file, ImmutableNode rootNode) throws IOException {
        // Take the current root node and check it
        String elementName = rootNode.getNodeName();
        boolean foundAMatch = false; // TODO: currently unnused, but when we also cover imports this could allow us to know which examples use elements that do not exist in the schema.

        for (Entry<String, Map<XmlSchemaElement, Set<String>>> entry : elementBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaElement, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    if (elementEntry.getKey().getName().contains(elementName)) {
                        elementEntry.getValue().add(file.getCanonicalPath());
                        foundAMatch = true;
                    }
                }
            }
        }

        // Then dip into the child elements
        for (ImmutableNode childNode : rootNode.getChildren()) {
            checkElement(file, childNode);
        }
    }

    /**
     * This code writes the bitmap to disk.
     */
    private static void printToCsv() throws IOException {
        FileWriter csvFileWriter = new FileWriter(outputFilePath);

        for (Entry<String, Map<XmlSchemaElement, Set<String>>> entry : elementBitmap.entrySet()) {
            if (entry.getValue() != null) {
                for (Entry<XmlSchemaElement, Set<String>> elementEntry : entry.getValue().entrySet()) {
                    csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append(elementEntry.getKey().getName()).append(File.pathSeparator)
                        .append(elementEntry.getValue().toString())
                        .append(System.lineSeparator());
                }
            } else {
                csvFileWriter.append(entry.getKey()).append(File.pathSeparator).append("N/A").append(File.pathSeparator)
                    .append("N/A")
                    .append(System.lineSeparator());
            }
        }
    }
}
